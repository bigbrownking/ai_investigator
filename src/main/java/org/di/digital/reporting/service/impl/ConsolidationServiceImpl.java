package org.di.digital.reporting.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.exception.NotFoundException;
import org.di.digital.model.user.Region;
import org.di.digital.reporting.config.ReportingClock;
import org.di.digital.reporting.dto.response.ConsolidatedRowResponse;
import org.di.digital.reporting.dto.response.ConsolidatedTableResponse;
import org.di.digital.reporting.mapper.SubmissionMapper;
import org.di.digital.reporting.mapper.TemplateMapper;
import org.di.digital.reporting.model.RegionalSubmission;
import org.di.digital.reporting.model.ReportTemplate;
import org.di.digital.reporting.model.enums.ReportRowStatus;
import org.di.digital.reporting.model.enums.TemplateStatus;
import org.di.digital.reporting.repository.RegionalSubmissionRepository;
import org.di.digital.reporting.repository.ReportTemplateRepository;
import org.di.digital.reporting.service.ConsolidationService;
import org.di.digital.repository.user.RegionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsolidationServiceImpl implements ConsolidationService {

    /** Every region keeps the same row in every table. */
    private static final Sort REGION_ORDER = Sort.by(Sort.Direction.ASC, "id");

    private final ReportTemplateRepository templateRepository;
    private final RegionalSubmissionRepository submissionRepository;
    private final RegionRepository regionRepository;
    private final TemplateMapper templateMapper;
    private final SubmissionMapper submissionMapper;
    private final ReportingClock clock;

    @Override
    public ConsolidatedTableResponse getConsolidated(String templateCode, LocalDate requestedDate) {
        LocalDate reportDate = requestedDate != null ? requestedDate : clock.today();
        ReportTemplate template = templateRepository.findByCodeAndStatus(templateCode, TemplateStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Report form " + templateCode + " has no active version"));

        // Rows of all template versions; if a region has two (saved before and after a publish), the newer wins
        Map<Long, RegionalSubmission> byRegion = submissionRepository
                .findByTemplate_CodeAndReportDate(templateCode, reportDate).stream()
                .collect(Collectors.toMap(RegionalSubmission::getRegionId, Function.identity(),
                        (a, b) -> a.getId() > b.getId() ? a : b));

        List<Region> regions = regionRepository.findAll(REGION_ORDER);
        List<ConsolidatedRowResponse> rows = new ArrayList<>();
        Map<ReportRowStatus, Integer> counts = new EnumMap<>(ReportRowStatus.class);
        for (Region region : regions) {
            ConsolidatedRowResponse row = submissionMapper.toConsolidatedRow(
                    rows.size() + 1, region, template, byRegion.remove(region.getId()));
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
}
