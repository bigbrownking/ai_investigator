package org.di.digital.reporting.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.exception.NotFoundException;
import org.di.digital.model.user.Region;
import org.di.digital.reporting.config.ReportingClock;
import org.di.digital.reporting.dto.response.ColumnDefinitionDto;
import org.di.digital.reporting.dto.response.ExportFile;
import org.di.digital.reporting.dto.response.ZonalConsolidatedRegion;
import org.di.digital.reporting.dto.response.ZonalConsolidatedResponse;
import org.di.digital.reporting.dto.response.ZonalSubmissionResponse;
import org.di.digital.reporting.dto.response.ZonalSubmissionSummaryResponse;
import org.di.digital.reporting.export.ZonalConsolidatedExcelWriter;
import org.di.digital.reporting.mapper.TemplateMapper;
import org.di.digital.reporting.mapper.ZonalMapper;
import org.di.digital.reporting.model.TableRow;
import org.di.digital.reporting.model.ZonalSubmission;
import org.di.digital.reporting.model.ZonalTemplate;
import org.di.digital.reporting.model.enums.ColumnType;
import org.di.digital.reporting.repository.ZonalSubmissionRepository;
import org.di.digital.reporting.repository.ZonalTemplateRepository;
import org.di.digital.reporting.service.ZonalReportAdminService;
import org.di.digital.repository.user.RegionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZonalReportAdminServiceImpl implements ZonalReportAdminService {

    private static final Sort REGION_ORDER = Sort.by(Sort.Direction.ASC, "id");

    private final ZonalSubmissionRepository submissionRepository;
    private final ZonalTemplateRepository templateRepository;
    private final RegionRepository regionRepository;
    private final TemplateMapper templateMapper;
    private final ZonalMapper zonalMapper;
    private final ReportingClock clock;
    private final ZonalConsolidatedExcelWriter excelWriter;

    @Override
    public Page<ZonalSubmissionSummaryResponse> getHistory(Long templateId, Long regionId, LocalDate from, LocalDate to,
                                                           int page, int size) {
        Specification<ZonalSubmission> spec = (root, query, cb) -> cb.conjunction();
        if (templateId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("templateId"), templateId));
        }
        if (regionId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("regionId"), regionId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("reportDate"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("reportDate"), to));
        }

        Page<ZonalSubmission> result = submissionRepository.findAll(spec, ZonalTableServiceImpl.pageRequest(page, size));
        Map<Long, Region> regions = regionsById(result.stream().map(ZonalSubmission::getRegionId).collect(Collectors.toSet()));
        return result.map(s -> zonalMapper.toSubmissionSummary(s, regions.get(s.getRegionId())));
    }

    @Override
    public ZonalSubmissionResponse getSubmission(Long submissionId) {
        ZonalSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Sent table not found: " + submissionId));
        Region region = regionRepository.findById(submission.getRegionId()).orElse(null);
        return zonalMapper.toSubmission(submission, region);
    }

    @Override
    public ZonalConsolidatedResponse getConsolidated(Long templateId, LocalDate requestedDate) {
        LocalDate asOf = requestedDate != null ? requestedDate : clock.today();
        ZonalTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Template not found: " + templateId));
        List<ColumnDefinitionDto> columns = templateMapper.toColumnDtos(template.getColumns());

        Map<Long, ZonalSubmission> byRegion = submissionRepository
                .findLatestPerRegion(templateId, asOf.plusDays(1).atStartOfDay()).stream()
                .collect(Collectors.toMap(ZonalSubmission::getRegionId, Function.identity()));

        List<Region> regions = regionRepository.findAll(REGION_ORDER);
        List<ZonalConsolidatedRegion> blocks = new ArrayList<>();
        Map<String, BigDecimal> totals = emptyTotals(columns);
        int submittedRegions = 0;
        int totalRows = 0;
        for (Region region : regions) {
            ZonalSubmission submission = byRegion.remove(region.getId());
            List<Map<String, Object>> rows = submission == null ? List.of() : toCurrentColumns(columns, submission);
            if (submission != null) {
                submittedRegions++;
            }
            totalRows += rows.size();
            addToTotals(totals, rows);
            blocks.add(ZonalConsolidatedRegion.builder()
                    .position(blocks.size() + 1)
                    .regionId(region.getId())
                    .regionNameRu(region.getRuName())
                    .regionNameKz(region.getKzName())
                    .submissionId(submission == null ? null : submission.getId())
                    .submittedAt(submission == null ? null : submission.getSubmittedAt())
                    .rows(rows)
                    .build());
        }
        if (!byRegion.isEmpty()) {
            log.warn("Consolidated zonal template {} on {}: submissions of unknown regions {} ignored",
                    templateId, asOf, byRegion.keySet());
        }

        return ZonalConsolidatedResponse.builder()
                .templateId(template.getId())
                .templateName(template.getName())
                .asOf(asOf)
                .columns(columns)
                .regions(blocks)
                .totalRegions(regions.size())
                .submittedRegions(submittedRegions)
                .totalRows(totalRows)
                .totals(totals)
                .generatedAt(clock.now())
                .build();
    }

    @Override
    public ExportFile exportConsolidated(Long templateId, LocalDate asOf) {
        ZonalConsolidatedResponse table = getConsolidated(templateId, asOf);
        return new ExportFile(ZonalConsolidatedExcelWriter.fileName(table),
                ZonalConsolidatedExcelWriter.CONTENT_TYPE,
                excelWriter.write(table));
    }

    private static List<Map<String, Object>> toCurrentColumns(List<ColumnDefinitionDto> columns, ZonalSubmission submission) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TableRow row : submission.getRows()) {
            Map<String, Object> cells = new LinkedHashMap<>();
            for (ColumnDefinitionDto column : columns) {
                Object value = row.getCells() == null ? null : row.getCells().get(column.getKey());
                cells.put(column.getKey(), column.getType() == ColumnType.NUMBER ? toNumber(value) : value);
            }
            rows.add(cells);
        }
        return rows;
    }

    private static Map<String, BigDecimal> emptyTotals(List<ColumnDefinitionDto> columns) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (ColumnDefinitionDto column : columns) {
            if (column.getType() == ColumnType.NUMBER) {
                totals.put(column.getKey(), BigDecimal.ZERO);
            }
        }
        return totals;
    }

    private static void addToTotals(Map<String, BigDecimal> totals, List<Map<String, Object>> rows) {
        for (Map<String, Object> cells : rows) {
            totals.replaceAll((key, sum) -> cells.get(key) instanceof BigDecimal number ? sum.add(number) : sum);
        }
    }

    private static Object toNumber(Object value) {
        if (value == null || value instanceof BigDecimal || !(value instanceof Number || value instanceof String)) {
            return value;
        }
        try {
            BigDecimal number = new BigDecimal(value.toString().trim());
            return number.scale() < 0 ? number.setScale(0) : number;
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private Map<Long, Region> regionsById(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return regionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Region::getId, Function.identity()));
    }
}