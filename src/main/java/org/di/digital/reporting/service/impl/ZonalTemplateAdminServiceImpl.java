package org.di.digital.reporting.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.exception.NotFoundException;
import org.di.digital.reporting.config.ReportingClock;
import org.di.digital.reporting.dto.request.ZonalColumnRequest;
import org.di.digital.reporting.dto.request.ZonalTemplateRequest;
import org.di.digital.reporting.dto.response.ZonalTemplateResponse;
import org.di.digital.reporting.dto.response.ZonalTemplateSummaryResponse;
import org.di.digital.reporting.mapper.TemplateMapper;
import org.di.digital.reporting.mapper.ZonalMapper;
import org.di.digital.reporting.model.ColumnDefinition;
import org.di.digital.reporting.model.TableRow;
import org.di.digital.reporting.model.ZonalTemplate;
import org.di.digital.reporting.model.ZonalWorksheet;
import org.di.digital.reporting.repository.ZonalSubmissionRepository;
import org.di.digital.reporting.repository.ZonalTemplateRepository;
import org.di.digital.reporting.repository.ZonalWorksheetRepository;
import org.di.digital.reporting.service.ZonalTemplateAdminService;
import org.di.digital.reporting.validation.ReportingValidationException;
import org.di.digital.reporting.validation.TemplateDefinitionValidator;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static org.di.digital.util.requests.UserUtil.getCurrentUser;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZonalTemplateAdminServiceImpl implements ZonalTemplateAdminService {

    static final int MAX_COLUMNS = 100;

    private final ZonalTemplateRepository templateRepository;
    private final ZonalWorksheetRepository worksheetRepository;
    private final ZonalSubmissionRepository submissionRepository;
    private final TemplateDefinitionValidator templateValidator;
    private final TemplateMapper templateMapper;
    private final ZonalMapper zonalMapper;
    private final ReportingClock clock;

    @Override
    public List<ZonalTemplateSummaryResponse> listTemplates() {
        return templateRepository.findAllByOrderByNameAsc().stream()
                .map(zonalMapper::toSummary)
                .toList();
    }

    @Override
    public ZonalTemplateResponse getTemplate(Long templateId) {
        return zonalMapper.toResponse(find(templateId));
    }

    @Override
    @Transactional
    public ZonalTemplateResponse createTemplate(ZonalTemplateRequest request) {
        String name = request.getName().trim();
        String nameKey = ZonalMapper.nameKey(name);
        if (templateRepository.existsByNameKey(nameKey)) {
            throw new IllegalStateException("A template named '" + name + "' already exists");
        }

        List<ColumnDefinition> columns;
        if ((request.getColumns() == null || request.getColumns().isEmpty()) && request.getCopyFromTemplateId() != null) {
            columns = templateMapper.copyColumns(find(request.getCopyFromTemplateId()).getColumns());
        } else {
            columns = buildColumns(request.getColumns(), Set.of());
        }

        LocalDateTime now = clock.now();
        Long userId = currentUserId();
        ZonalTemplate template = ZonalTemplate.builder()
                .name(name)
                .nameKey(nameKey)
                .description(request.getDescription())
                .columns(columns)
                .active(request.getActive() == null || request.getActive())
                .createdBy(userId)
                .updatedBy(userId)
                .createdAt(now)
                .updatedAt(now)
                .build();

        ZonalTemplate saved = templateRepository.saveAndFlush(template);
        log.info("Zonal template {} '{}' created by admin {} ({} columns, active={})",
                saved.getId(), name, userId, columns.size(), saved.isActive());
        return zonalMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ZonalTemplateResponse updateTemplate(Long templateId, ZonalTemplateRequest request) {
        ZonalTemplate template = find(templateId);
        if (request.getLockVersion() != null && !request.getLockVersion().equals(template.getLockVersion())) {
            throw new ObjectOptimisticLockingFailureException(ZonalTemplate.class, templateId);
        }

        String name = request.getName().trim();
        String nameKey = ZonalMapper.nameKey(name);
        if (templateRepository.existsByNameKeyAndIdNot(nameKey, templateId)) {
            throw new IllegalStateException("A template named '" + name + "' already exists");
        }

        Set<String> existingKeys = new HashSet<>();
        template.getColumns().forEach(c -> existingKeys.add(c.getKey()));
        List<ColumnDefinition> columns = buildColumns(request.getColumns(), existingKeys);

        Set<String> keptKeys = new HashSet<>();
        columns.forEach(c -> keptKeys.add(c.getKey()));
        boolean columnsRemoved = !keptKeys.containsAll(existingKeys);

        template.setName(name);
        template.setNameKey(nameKey);
        template.setDescription(request.getDescription());
        template.setColumns(columns);
        template.setUpdatedAt(clock.now());
        template.setUpdatedBy(currentUserId());
        ZonalTemplate saved = templateRepository.saveAndFlush(template);

        if (columnsRemoved) {
            pruneRemovedColumns(templateId, keptKeys);
        }
        log.info("Zonal template {} '{}' updated by admin {} ({} columns)", templateId, name, currentUserId(), columns.size());
        return zonalMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ZonalTemplateResponse setActive(Long templateId, boolean active) {
        ZonalTemplate template = find(templateId);
        if (template.isActive() != active) {
            template.setActive(active);
            template.setUpdatedAt(clock.now());
            template.setUpdatedBy(currentUserId());
            template = templateRepository.saveAndFlush(template);
            log.info("Zonal template {} '{}' {} by admin {}",
                    templateId, template.getName(), active ? "activated" : "deactivated", currentUserId());
        }
        return zonalMapper.toResponse(template);
    }

    @Override
    @Transactional
    public void deleteTemplate(Long templateId) {
        ZonalTemplate template = find(templateId);
        if (submissionRepository.existsByTemplateId(templateId)) {
            throw new IllegalStateException("Template '" + template.getName()
                    + "' has already been sent by regions and is kept for history; deactivate it instead");
        }
        worksheetRepository.deleteByTemplateId(templateId);
        templateRepository.delete(template);
        log.info("Zonal template {} '{}' deleted by admin {}", templateId, template.getName(), currentUserId());
    }

    private void pruneRemovedColumns(Long templateId, Set<String> keptKeys) {
        for (ZonalWorksheet worksheet : worksheetRepository.findByTemplateId(templateId)) {
            List<TableRow> rows = new ArrayList<>();
            for (TableRow row : worksheet.getRows()) {
                Map<String, Object> cells = new LinkedHashMap<>(row.getCells());
                cells.keySet().retainAll(keptKeys);
                rows.add(TableRow.builder().id(row.getId()).cells(cells).build());
            }
            worksheet.setRows(rows);
            worksheetRepository.save(worksheet);
        }
    }

    private List<ColumnDefinition> buildColumns(List<ZonalColumnRequest> requests, Set<String> existingKeys) {
        List<ZonalColumnRequest> input = requests == null ? List.of() : requests.stream().filter(Objects::nonNull).toList();
        if (input.size() > MAX_COLUMNS) {
            throw new ReportingValidationException(List.of("A template can have at most " + MAX_COLUMNS + " columns"));
        }

        Set<String> used = new HashSet<>(existingKeys);
        input.forEach(c -> {
            if (c.getKey() != null && !c.getKey().isBlank()) {
                used.add(c.getKey().trim());
            }
        });
        for (ZonalColumnRequest column : input) {
            if (column.getKey() == null || column.getKey().isBlank()) {
                column.setKey(newKey(used));
            }
        }

        List<ColumnDefinition> columns = zonalMapper.toColumns(input);
        templateValidator.validateColumns(columns);
        return columns;
    }

    private static String newKey(Set<String> used) {
        String key;
        do {
            key = "c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        } while (!used.add(key));
        return key;
    }

    private ZonalTemplate find(Long templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Template not found: " + templateId));
    }

    private static Long currentUserId() {
        return getCurrentUser().getId();
    }
}
