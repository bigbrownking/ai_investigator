package org.di.digital.reporting.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.reporting.dto.request.CreateTemplateRequest;
import org.di.digital.reporting.mapper.TemplateMapper;
import org.di.digital.reporting.model.ReportTemplate;
import org.di.digital.reporting.model.enums.TemplateStatus;
import org.di.digital.reporting.repository.ReportTemplateRepository;
import org.di.digital.reporting.validation.ReportingValidationException;
import org.di.digital.reporting.validation.TemplateDefinitionValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Loads the fixed consolidated-table templates (columns only) from {@value #SEED_LOCATION} as ACTIVE version 1.
 * A form whose code already exists (in any status) is left untouched, so admin changes survive restarts.
 * Errors are logged, never thrown: a broken seed file must not stop the whole application.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class ReportingTemplateSeeder implements ApplicationRunner {

    static final String SEED_LOCATION = "reporting/template-seed.json";

    private final ReportTemplateRepository templateRepository;
    private final TemplateMapper templateMapper;
    private final TemplateDefinitionValidator templateValidator;
    private final ReportingClock clock;

    @Value("${reporting.seed-templates.enabled:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Reporting template seeding is disabled");
            return;
        }

        List<CreateTemplateRequest> seeds;
        try {
            seeds = readSeeds();
        } catch (IOException e) {
            // Also covers a missing file (FileNotFoundException) and malformed JSON (JsonProcessingException)
            log.error("Reporting template seeding skipped: cannot read {}", SEED_LOCATION, e);
            return;
        }
        if (seeds == null) {
            log.error("Reporting template seeding skipped: {} contains no template list", SEED_LOCATION);
            return;
        }

        int created = 0;
        int skipped = 0;
        for (CreateTemplateRequest seed : seeds) {
            if (seedTemplate(seed)) {
                created++;
            } else {
                skipped++;
            }
        }
        log.info("Reporting templates seeded: {} created, {} skipped, {} in {}",
                created, skipped, seeds.size(), SEED_LOCATION);
    }

    private boolean seedTemplate(CreateTemplateRequest seed) {
        if (seed == null) {
            log.error("Empty entry in {} skipped", SEED_LOCATION);
            return false;
        }
        String code = seed.getCode() == null ? null : seed.getCode().trim();
        if (code == null || code.isEmpty()) {
            log.error("Seed template without code skipped: {}", seed.getName());
            return false;
        }

        try {
            if (templateRepository.existsByCode(code)) {
                return false;
            }
            LocalDateTime now = clock.now();
            ReportTemplate template = ReportTemplate.builder()
                    .code(code)
                    .version(1)
                    .name(seed.getName() == null ? null : seed.getName().trim())
                    .description(seed.getDescription())
                    .status(TemplateStatus.ACTIVE)
                    .columns(templateMapper.toColumns(seed.getColumns()))
                    .createdAt(now)
                    .updatedAt(now)
                    .publishedAt(now)
                    .build();
            templateValidator.validateForPublish(template);
            templateRepository.save(template);
            log.info("Report form {} seeded as ACTIVE v1", code);
            return true;
        } catch (ReportingValidationException e) {
            log.error("Seed template {} is invalid and was skipped: {}", code, e.getErrors());
        } catch (DataIntegrityViolationException e) {
            // Another instance seeded the same form concurrently
            log.info("Report form {} was seeded concurrently, skipped", code);
        } catch (RuntimeException e) {
            // e.g. a malformed column in the seed file, or a database error
            log.error("Seed template {} could not be created and was skipped", code, e);
        }
        return false;
    }

    private List<CreateTemplateRequest> readSeeds() throws IOException {
        try (InputStream in = new ClassPathResource(SEED_LOCATION).getInputStream()) {
            return new ObjectMapper().readValue(in, new TypeReference<List<CreateTemplateRequest>>() {});
        }
    }
}
