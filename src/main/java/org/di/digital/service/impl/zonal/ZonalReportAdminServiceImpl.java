package org.di.digital.service.impl.zonal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.config.ReportingClock;
import org.di.digital.dto.response.zonal.*;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.AccessDeniedMessage;
import org.di.digital.model.enums.zonal.ColumnType;
import org.di.digital.model.user.Region;
import org.di.digital.model.zonal.TableRow;
import org.di.digital.model.zonal.ZonalSubmission;
import org.di.digital.model.zonal.ZonalTemplate;
import org.di.digital.repository.user.RegionRepository;
import org.di.digital.repository.zonal.ZonalSubmissionRepository;
import org.di.digital.repository.zonal.ZonalTemplateRepository;
import org.di.digital.security.zonal.ReportingAccess;
import org.di.digital.service.export.zonal.ZonalConsolidatedExcelWriter;
import org.di.digital.service.zonal.ZonalReportAdminService;
import org.di.digital.util.mapper.TemplateMapper;
import org.di.digital.util.mapper.ZonalMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.di.digital.util.requests.UserUtil.getCurrentLang;

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
    private final ReportingAccess reportingAccess;

    @Override
    public Page<ZonalSubmissionSummaryResponse> getHistory(Long templateId, Long regionId, LocalDate from, LocalDate to,
                                                           int page, int size) {
        Specification<ZonalSubmission> spec = (root, query, cb) -> cb.conjunction();
        Set<Long> visibleRegionIds = reportingAccess.visibleRegionIds();
        if (visibleRegionIds != null) {
            if (regionId != null && !visibleRegionIds.contains(regionId)) {
                throw new AccessDeniedException(AccessDeniedMessage.REGION_OUT_OF_REGION.localized(getCurrentLang()));
            }
            spec = spec.and((root, query, cb) -> root.get("regionId").in(visibleRegionIds));
        }
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
        Set<Long> visibleRegionIds = reportingAccess.visibleRegionIds();
        if (visibleRegionIds != null && !visibleRegionIds.contains(submission.getRegionId())) {
            // не раскрываем, что таблица существует в чужом регионе
            throw new NotFoundException("Sent table not found: " + submissionId);
        }
        Region region = regionRepository.findById(submission.getRegionId()).orElse(null);
        return zonalMapper.toSubmission(submission, region);
    }

//    @Override
//    public ZonalConsolidatedResponse getConsolidated(Long templateId, LocalDate requestedDate, String regionName) {
//        LocalDate asOf = requestedDate != null ? requestedDate : clock.today();
//        ZonalTemplate template = templateRepository.findById(templateId)
//                .orElseThrow(() -> new NotFoundException("Template not found: " + templateId));
//        List<ColumnDefinitionDto> columns = templateMapper.toColumnDtos(template.getColumns());
//
//        Map<Long, ZonalSubmission> byRegion = submissionRepository
//                .findLatestPerRegion(templateId, asOf.plusDays(1).atStartOfDay()).stream()
//                .collect(Collectors.toMap(ZonalSubmission::getRegionId, Function.identity()));
//
//
//
//
//        // Сначала департаменты (по id), последним Агентство (регион АФМ). Нумерация сквозная: 1..N, затем N+1.
//        List<Region> ordered = new ArrayList<>();
//        Region agency = null;
//        for (Region region : regionRepository.findAll(REGION_ORDER)) {
//            if (region.getId() == ReportingAccess.AFM_REGION_ID) {
//                agency = region;
//            } else {
//                ordered.add(region);
//            }
//        }
//        if (agency != null) {
//            ordered.add(agency);
//        }
//
//        // null: все департаменты (ADMIN и рег-админ АФМ); иначе только департамент пользователя
//        Set<Long> visibleRegionIds = reportingAccess.visibleRegionIds();
//
//
//        String search = regionName == null
//                ? ""
//                : regionName.trim().toLowerCase(Locale.ROOT);
//
//        List<Region> filteredRegions = ordered.stream()
//                .filter(region ->
//                        visibleRegionIds == null
//                                || visibleRegionIds.contains(region.getId())
//                )
//                .filter(region -> {
//                    if (search.isEmpty()) {
//                        return true;
//                    }
//
//                    String ruName = region.getRuName();
//                    String kzName = region.getKzName();
//
//                    return (ruName != null
//                            && ruName.toLowerCase(Locale.ROOT).contains(search))
//                            || (kzName != null
//                            && kzName.toLowerCase(Locale.ROOT).contains(search));
//                })
//                .toList();
//
//
//
////        List<Region> regions = regionRepository.findAll(REGION_ORDER);
//        List<ZonalConsolidatedRegion> blocks = new ArrayList<>();
//        Map<String, BigDecimal> totals = emptyTotals(columns);
//        int submittedRegions = 0;
//        int totalRows = 0;
//        int position = 0;
//        for (Region region : filteredRegions) {
//            position++;
//
//            ZonalSubmission submission = byRegion.remove(region.getId());
//
//            boolean isAgency =
//                    region.getId() == ReportingAccess.AFM_REGION_ID;
//
//            List<Map<String, Object>> rows =
//                    submission == null
//                            ? List.of()
//                            : toCurrentColumns(columns, submission);
//
//            if (submission != null) {
//                submittedRegions++;
//            }
//
//            totalRows += rows.size();
//            addToTotals(totals, rows);
//
//            blocks.add(ZonalConsolidatedRegion.builder()
//                    .position(position)
//                    .agency(isAgency)
//                    .regionId(region.getId())
//                    .regionNameRu(region.getRuName())
//                    .regionNameKz(region.getKzName())
//                    .submissionId(
//                            submission == null ? null : submission.getId()
//                    )
//                    .submittedAt(
//                            submission == null ? null : submission.getSubmittedAt()
//                    )
//                    .rows(rows)
//                    .build());
//        }
//        if (!byRegion.isEmpty()) {
//            log.warn("Consolidated zonal template {} on {}: submissions of unknown regions {} ignored",
//                    templateId, asOf, byRegion.keySet());
//        }
//
//        return ZonalConsolidatedResponse.builder()
//                .templateId(template.getId())
//                .templateName(template.getName())
//                .asOf(asOf)
//                .columns(columns)
//                .regions(blocks)
//                .totalRegions(blocks.size())
//                .submittedRegions(submittedRegions)
//                .totalRows(totalRows)
//                .totals(totals)
//                .generatedAt(clock.now())
//                .build();
//    }


    @Override
    public ZonalConsolidatedResponse getConsolidated(
            Long templateId,
            LocalDate requestedDate,
            String regionName) {

        LocalDate asOf = resolveReportDate(requestedDate);

        ZonalTemplate template = getTemplateOrThrow(templateId);

        List<ColumnDefinitionDto> columns =
                getTemplateColumns(template);

        Map<Long, ZonalSubmission> submissionsByRegion =
                getLatestSubmissionsByRegion(templateId, asOf);

        List<Region> regions =
                getOrderedRegions();

        List<Region> filteredRegions =
                filterRegions(regions, regionName);

        Set<Long> visibleRegionIds =
                reportingAccess.visibleRegionIds();

        List<Region> accessibleRegions =
                filterAccessibleRegions(filteredRegions, visibleRegionIds);

        logUnknownSubmissionRegions(
                templateId, asOf, regions, submissionsByRegion);

        return buildConsolidatedResponse(
                template,
                asOf,
                columns,
                accessibleRegions,
                submissionsByRegion
        );
    }


    private LocalDate resolveReportDate(LocalDate requestedDate) {
        return requestedDate != null
                ? requestedDate
                : clock.today();
    }

    private ZonalTemplate getTemplateOrThrow(Long templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "Template not found: " + templateId
                        ));
    }

    private List<ColumnDefinitionDto> getTemplateColumns(
            ZonalTemplate template) {

        return templateMapper.toColumnDtos(template.getColumns());
    }


    private Map<Long, ZonalSubmission> getLatestSubmissionsByRegion(
            Long templateId,
            LocalDate asOf) {

        return submissionRepository
                .findLatestPerRegion(
                        templateId,
                        asOf.plusDays(1).atStartOfDay()
                )
                .stream()
                .collect(Collectors.toMap(
                        ZonalSubmission::getRegionId,
                        Function.identity()
                ));
    }

    private List<Region> getOrderedRegions() {
        List<Region> regions = new ArrayList<>();
        Region agency = null;

        for (Region region : regionRepository.findAll(REGION_ORDER)) {
            if (region.getId() == ReportingAccess.AFM_REGION_ID) {
                agency = region;
            } else {
                regions.add(region);
            }
        }

        if (agency != null) {
            regions.add(agency);
        }

        return regions;
    }


    private List<Region> filterRegions(
            List<Region> regions,
            String regionName) {

        if (regionName == null || regionName.isBlank()) {
            return regions;
        }

        String search = regionName.trim().toLowerCase(Locale.ROOT);

        return regions.stream()
                .filter(region ->
                        containsIgnoreCase(region.getRuName(), search)
                                || containsIgnoreCase(region.getKzName(), search)
                )
                .toList();
    }

    private boolean containsIgnoreCase(String value, String search) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(search);
    }

    private List<Region> filterAccessibleRegions(
            List<Region> regions,
            Set<Long> visibleRegionIds) {

        if (visibleRegionIds == null) {
            return regions;
        }

        return regions.stream()
                .filter(region ->
                        visibleRegionIds.contains(region.getId())
                )
                .toList();
    }

    private ZonalConsolidatedResponse buildConsolidatedResponse(
            ZonalTemplate template,
            LocalDate asOf,
            List<ColumnDefinitionDto> columns,
            List<Region> regions,
            Map<Long, ZonalSubmission> submissionsByRegion) {

        List<ZonalConsolidatedRegion> blocks = new ArrayList<>();
        Map<String, BigDecimal> totals = emptyTotals(columns);

        int submittedRegions = 0;
        int totalRows = 0;
        int position = 0;

        for (Region region : regions) {
            ZonalSubmission submission =
                    submissionsByRegion.get(region.getId());

            List<Map<String, Object>> rows =
                    submission == null
                            ? List.of()
                            : toCurrentColumns(columns, submission);

            if (submission != null) {
                submittedRegions++;
            }

            totalRows += rows.size();
            addToTotals(totals, rows);

            blocks.add(ZonalConsolidatedRegion.builder()
                    .position(++position)
                    .agency(
                            region.getId() == ReportingAccess.AFM_REGION_ID
                    )
                    .regionId(region.getId())
                    .regionNameRu(region.getRuName())
                    .regionNameKz(region.getKzName())
                    .submissionId(
                            submission == null ? null : submission.getId()
                    )
                    .submittedAt(
                            submission == null
                                    ? null
                                    : submission.getSubmittedAt()
                    )
                    .rows(rows)
                    .build());
        }

        return ZonalConsolidatedResponse.builder()
                .templateId(template.getId())
                .templateName(template.getName())
                .asOf(asOf)
                .columns(columns)
                .regions(blocks)
                .totalRegions(blocks.size())
                .submittedRegions(submittedRegions)
                .totalRows(totalRows)
                .totals(totals)
                .generatedAt(clock.now())
                .build();
    }

    private void logUnknownSubmissionRegions(
            Long templateId,
            LocalDate asOf,
            List<Region> regions,
            Map<Long, ZonalSubmission> submissionsByRegion) {

        Set<Long> knownRegionIds = regions.stream()
                .map(Region::getId)
                .collect(Collectors.toSet());

        Set<Long> unknownRegionIds = new HashSet<>(
                submissionsByRegion.keySet()
        );

        unknownRegionIds.removeAll(knownRegionIds);

        if (!unknownRegionIds.isEmpty()) {
            log.warn(
                    "Consolidated zonal template {} on {}: "
                            + "submissions of unknown regions {} ignored",
                    templateId,
                    asOf,
                    unknownRegionIds
            );
        }
    }

    @Override
    public ExportFile exportConsolidated(Long templateId, LocalDate asOf) {
        ZonalConsolidatedResponse table = getConsolidated(templateId, asOf, null);
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