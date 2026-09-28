package org.di.digital.reporting.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.exception.NotFoundException;
import org.di.digital.reporting.config.ReportingClock;
import org.di.digital.reporting.dto.response.ConsolidatedRowResponse;
import org.di.digital.reporting.dto.response.ConsolidatedTableResponse;
import org.di.digital.reporting.integration.RegionDirectory;
import org.di.digital.reporting.integration.RegionInfo;
import org.di.digital.reporting.mapper.TemplateMapper;
import org.di.digital.reporting.model.ColumnDefinition;
import org.di.digital.reporting.model.RegionalSubmission;
import org.di.digital.reporting.model.ReportTemplate;
import org.di.digital.reporting.model.enums.ReportRowStatus;
import org.di.digital.reporting.model.enums.SubmissionStatus;
import org.di.digital.reporting.model.enums.TemplateStatus;
import org.di.digital.reporting.repository.RegionalSubmissionRepository;
import org.di.digital.reporting.repository.ReportTemplateRepository;
import org.di.digital.reporting.service.ConsolidationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsolidationServiceImpl implements ConsolidationService {

    private final ReportTemplateRepository templateRepository;
    private final RegionalSubmissionRepository submissionRepository;
    private final RegionDirectory regionDirectory;
    private final TemplateMapper templateMapper;
    private final ReportingClock clock;

    @Override
    public ConsolidatedTableResponse getConsolidated(String templateCode, LocalDate requestedDate) {
        LocalDate reportDate = requestedDate != null ? requestedDate : clock.today();
        ReportTemplate template = templateRepository.findByCodeAndStatus(templateCode, TemplateStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Report form " + templateCode + " has no active version"));

        Map<Long, RegionalSubmission> byRegion = submissionRepository
                .findByTemplateCodeAndReportDate(templateCode, reportDate).stream()
                .collect(Collectors.toMap(RegionalSubmission::getRegionId, Function.identity()));

        List<RegionInfo> regions = regionDirectory.orderedRegions();
        List<ConsolidatedRowResponse> rows = new ArrayList<>();
        Map<ReportRowStatus, Integer> counts = new EnumMap<>(ReportRowStatus.class);
        for (RegionInfo region : regions) {
            RegionalSubmission submission = byRegion.remove(region.id());
            ConsolidatedRowResponse row = toRow(rows.size() + 1, region, submission, template.getColumns());
            counts.merge(row.getStatus(), 1, Integer::sum);
            rows.add(row);
        }
        if (!byRegion.isEmpty()) {
            log.warn("Consolidated {} on {}: rows of unknown regions {} ignored", templateCode, reportDate, byRegion.keySet());
        }

        return ConsolidatedTableResponse.builder()
                .templateCode(template.getCode())
                .templateName(template.getName())
                .templateVersion(template.getVersion())
                .reportDate(reportDate)
                .columns(templateMapper.toColumnDtos(template.getColumns()))
                .rows(rows)
                .totalRegions(regions.size())
                .submittedRegions(counts.getOrDefault(ReportRowStatus.SUBMITTED, 0))
                .draftRegions(counts.getOrDefault(ReportRowStatus.DRAFT, 0))
                .notSubmittedRegions(counts.getOrDefault(ReportRowStatus.NOT_SUBMITTED, 0))
                .generatedAt(clock.now())
                .build();
    }

    private ConsolidatedRowResponse toRow(int position, RegionInfo region, RegionalSubmission submission,
                                          List<ColumnDefinition> columns) {
        boolean isSubmitted = submission != null && submission.getStatus() == SubmissionStatus.SUBMITTED;
        ReportRowStatus status = submission == null ? ReportRowStatus.NOT_SUBMITTED
                : isSubmitted ? ReportRowStatus.SUBMITTED : ReportRowStatus.DRAFT;

        // Every current column is present; drafts are not shown to the admin
        Map<String, Object> cells = new LinkedHashMap<>();
        for (ColumnDefinition column : columns) {
            cells.put(column.getKey(), isSubmitted ? submission.getCells().get(column.getKey()) : null);
        }

        return ConsolidatedRowResponse.builder()
                .position(position)
                .regionId(region.id())
                .regionNameRu(region.nameRu())
                .regionNameKz(region.nameKz())
                .status(status)
                .cells(cells)
                .submittedAt(isSubmitted ? submission.getSubmittedAt() : null)
                .build();
    }
}
