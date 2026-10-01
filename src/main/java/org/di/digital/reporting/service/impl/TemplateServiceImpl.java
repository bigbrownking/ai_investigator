package org.di.digital.reporting.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.exception.NotFoundException;
import org.di.digital.reporting.config.ReportingClock;
import org.di.digital.reporting.dto.request.CreateTemplateRequest;
import org.di.digital.reporting.dto.request.UpdateTemplateRequest;
import org.di.digital.reporting.dto.response.ActiveFormResponse;
import org.di.digital.reporting.dto.response.TemplateResponse;
import org.di.digital.reporting.dto.response.TemplateSummaryResponse;
import org.di.digital.reporting.integration.ReportingUserContext;
import org.di.digital.reporting.mapper.TemplateMapper;
import org.di.digital.reporting.model.ReportTemplate;
import org.di.digital.reporting.model.enums.TemplateStatus;
import org.di.digital.reporting.repository.ReportTemplateRepository;
import org.di.digital.reporting.service.TemplateService;
import org.di.digital.reporting.validation.TemplateDefinitionValidator;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Concurrency is guarded by unique constraints / partial indexes and @Version; conflicts surface as 409.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateServiceImpl implements TemplateService {

    private final ReportTemplateRepository templateRepository;
    private final TemplateDefinitionValidator templateValidator;
    private final TemplateMapper templateMapper;
    private final ReportingUserContext userContext;
    private final ReportingClock clock;

    @Override
    public List<TemplateSummaryResponse> listForms() {
        Map<String, List<ReportTemplate>> byCode = templateRepository
                .findAll(Sort.by("code").ascending().and(Sort.by("version").descending()))
                .stream()
                .collect(Collectors.groupingBy(ReportTemplate::getCode, LinkedHashMap::new, Collectors.toList()));

        return byCode.entrySet().stream()
                .map(entry -> toSummary(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public List<ActiveFormResponse> listActiveForms() {
        return templateRepository.findByStatusOrderByCodeAsc(TemplateStatus.ACTIVE).stream()
                .map(templateMapper::toActiveForm)
                .toList();
    }

    @Override
    public List<TemplateResponse> getVersions(String code) {
        List<ReportTemplate> versions = templateRepository.findByCodeOrderByVersionDesc(code);
        if (versions.isEmpty()) {
            throw new NotFoundException("Report form not found: " + code);
        }
        return versions.stream().map(templateMapper::toResponse).toList();
    }

    @Override
    public TemplateResponse getVersion(String code, Integer version) {
        return templateMapper.toResponse(findVersion(code, version));
    }

    @Override
    public TemplateResponse getActive(String code) {
        ReportTemplate active = templateRepository.findByCodeAndStatus(code, TemplateStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Report form " + code + " has no active version"));
        return templateMapper.toResponse(active);
    }

    @Override
    @Transactional
    public TemplateResponse createForm(CreateTemplateRequest request) {
        String code = request.getCode().trim();
        if (templateRepository.existsByCode(code)) {
            throw new IllegalStateException("Report form already exists: " + code);
        }

        LocalDateTime now = clock.now();
        ReportTemplate template = ReportTemplate.builder()
                .code(code)
                .version(1)
                .name(request.getName().trim())
                .description(request.getDescription())
                .status(TemplateStatus.DRAFT)
                .columns(templateMapper.toColumns(request.getColumns()))
                .createdBy(userContext.currentOperatorId())
                .createdAt(now)
                .updatedAt(now)
                .build();
        templateValidator.validateDraft(template);

        ReportTemplate saved = templateRepository.save(template);
        log.info("Report form {} created (v1, DRAFT)", code);
        return templateMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TemplateResponse createDraft(String code) {
        templateRepository.findByCodeAndStatus(code, TemplateStatus.DRAFT).ifPresent(draft -> {
            throw new IllegalStateException(
                    "Report form " + code + " already has a draft: version " + draft.getVersion());
        });
        ReportTemplate latest = templateRepository.findFirstByCodeOrderByVersionDesc(code)
                .orElseThrow(() -> new NotFoundException("Report form not found: " + code));

        LocalDateTime now = clock.now();
        ReportTemplate draft = ReportTemplate.builder()
                .code(code)
                .version(latest.getVersion() + 1)
                .name(latest.getName())
                .description(latest.getDescription())
                .status(TemplateStatus.DRAFT)
                .columns(templateMapper.copyColumns(latest.getColumns()))
                .createdBy(userContext.currentOperatorId())
                .createdAt(now)
                .updatedAt(now)
                .build();

        ReportTemplate saved = templateRepository.save(draft);
        log.info("Report form {}: draft v{} created from v{}", code, saved.getVersion(), latest.getVersion());
        return templateMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TemplateResponse updateDraft(String code, Integer version, UpdateTemplateRequest request) {
        ReportTemplate draft = findDraft(code, version);
        if (request.getLockVersion() != null && !request.getLockVersion().equals(draft.getLockVersion())) {
            throw new ObjectOptimisticLockingFailureException(ReportTemplate.class, draft.getId());
        }

        draft.setName(request.getName().trim());
        draft.setDescription(request.getDescription());
        draft.setColumns(templateMapper.toColumns(request.getColumns()));
        draft.setUpdatedAt(clock.now());
        templateValidator.validateDraft(draft);

        // Flush so the response carries the incremented lockVersion
        return templateMapper.toResponse(templateRepository.saveAndFlush(draft));
    }

    @Override
    @Transactional
    public void deleteDraft(String code, Integer version) {
        ReportTemplate draft = findDraft(code, version);
        templateRepository.delete(draft);
        log.info("Report form {}: draft v{} deleted", code, version);
    }

    @Override
    @Transactional
    public TemplateResponse publish(String code, Integer version) {
        ReportTemplate draft = findDraft(code, version);
        templateValidator.validateForPublish(draft);

        LocalDateTime now = clock.now();
        // Archive and flush first: the partial unique index allows only one ACTIVE version per form
        // and is checked per statement. Both updates share one transaction.
        Optional<ReportTemplate> archived = templateRepository.findByCodeAndStatus(code, TemplateStatus.ACTIVE)
                .map(active -> {
                    active.setStatus(TemplateStatus.ARCHIVED);
                    active.setArchivedAt(now);
                    return templateRepository.saveAndFlush(active);
                });

        draft.setStatus(TemplateStatus.ACTIVE);
        draft.setPublishedAt(now);
        draft.setUpdatedAt(now);
        ReportTemplate published = templateRepository.saveAndFlush(draft);

        log.info("Report form {}: v{} published{}", code, version,
                archived.map(a -> ", v" + a.getVersion() + " archived").orElse(""));
        return templateMapper.toResponse(published);
    }

    private ReportTemplate findVersion(String code, Integer version) {
        return templateRepository.findByCodeAndVersion(code, version)
                .orElseThrow(() -> new NotFoundException("Report form " + code + " v" + version + " not found"));
    }

    private ReportTemplate findDraft(String code, Integer version) {
        ReportTemplate template = findVersion(code, version);
        if (template.getStatus() != TemplateStatus.DRAFT) {
            throw new IllegalStateException(
                    "Report form " + code + " v" + version + " is " + template.getStatus() + ", only DRAFT can be changed");
        }
        return template;
    }

    private TemplateSummaryResponse toSummary(String code, List<ReportTemplate> versions) {
        Optional<ReportTemplate> active = versions.stream()
                .filter(t -> t.getStatus() == TemplateStatus.ACTIVE)
                .findFirst();
        Optional<ReportTemplate> draft = versions.stream()
                .filter(t -> t.getStatus() == TemplateStatus.DRAFT)
                .findFirst();
        ReportTemplate latest = versions.stream()
                .max(Comparator.comparing(ReportTemplate::getVersion))
                .orElseThrow();

        return TemplateSummaryResponse.builder()
                .code(code)
                .name(active.orElse(latest).getName())
                .activeVersion(active.map(ReportTemplate::getVersion).orElse(null))
                .activePublishedAt(active.map(ReportTemplate::getPublishedAt).orElse(null))
                .draftVersion(draft.map(ReportTemplate::getVersion).orElse(null))
                .latestVersion(latest.getVersion())
                .build();
    }
}
