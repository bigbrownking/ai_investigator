package org.di.digital.reporting.service.impl;

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
import org.di.digital.reporting.model.SelectOption;
import org.di.digital.reporting.model.enums.ColumnType;
import org.di.digital.reporting.model.enums.ReportRowStatus;
import org.di.digital.reporting.model.enums.SubmissionStatus;
import org.di.digital.reporting.model.enums.TemplateStatus;
import org.di.digital.reporting.repository.RegionalSubmissionRepository;
import org.di.digital.reporting.repository.ReportTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsolidationServiceImplTest {

    private static final String CODE = "form_1";
    private static final LocalDate DATE = LocalDate.of(2026, 9, 28);

    // 2026-09-27 20:30 UTC is already 2026-09-28 01:30 in Astana (UTC+5)
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T20:30:00Z"), ZoneId.of("Asia/Almaty"));

    private static final List<RegionInfo> REGIONS = List.of(
            new RegionInfo(1L, "город Астана", "Астана қаласы"),
            new RegionInfo(2L, "город Алматы", "Алматы қаласы"),
            new RegionInfo(3L, "город Шымкент", "Шымкент қаласы"),
            new RegionInfo(4L, "Акмолинская область", "Ақмола облысы"));

    @Mock
    private ReportTemplateRepository templateRepository;
    @Mock
    private RegionalSubmissionRepository submissionRepository;
    @Mock
    private RegionDirectory regionDirectory;

    private ConsolidationServiceImpl service;
    private ReportTemplate template;

    @BeforeEach
    void setUp() {
        service = new ConsolidationServiceImpl(templateRepository, submissionRepository, regionDirectory,
                new TemplateMapper(), new ReportingClock(CLOCK));
        template = ReportTemplate.builder()
                .id(10L).code(CODE).name("Сводная форма 1").version(3).status(TemplateStatus.ACTIVE)
                .columns(new ArrayList<>(List.of(
                        ColumnDefinition.builder().key("amount").label("Сумма").type(ColumnType.NUMBER)
                                .required(true).order(1).build(),
                        ColumnDefinition.builder().key("state").label("Статус").type(ColumnType.SELECT)
                                .required(true).order(2)
                                .options(new ArrayList<>(List.of(new SelectOption("DONE", "Выполнено"))))
                                .build())))
                .build();
    }

    @Test
    void buildsOneRowPerRegionInDirectoryOrderWithStatusesCellsAndCounts() {
        when(templateRepository.findByCodeAndStatus(CODE, TemplateStatus.ACTIVE)).thenReturn(Optional.of(template));
        when(regionDirectory.orderedRegions()).thenReturn(REGIONS);
        LocalDateTime submittedAt = LocalDateTime.of(2026, 9, 28, 10, 15);
        when(submissionRepository.findByTemplateCodeAndReportDate(CODE, DATE)).thenReturn(List.of(
                submission(2L, SubmissionStatus.SUBMITTED, Map.of("amount", 1500, "state", "DONE", "old_column", 7), submittedAt),
                submission(3L, SubmissionStatus.DRAFT, Map.of("amount", "черновик"), null)));

        ConsolidatedTableResponse table = service.getConsolidated(CODE, DATE);

        assertThat(table.getTemplateCode()).isEqualTo(CODE);
        assertThat(table.getTemplateName()).isEqualTo("Сводная форма 1");
        assertThat(table.getTemplateVersion()).isEqualTo(3);
        assertThat(table.getReportDate()).isEqualTo(DATE);
        assertThat(table.getColumns()).extracting("key").containsExactly("amount", "state");
        assertThat(table.getColumns().get(1).getOptions()).extracting("value").containsExactly("DONE");

        List<ConsolidatedRowResponse> rows = table.getRows();
        assertThat(rows).extracting(ConsolidatedRowResponse::getPosition).containsExactly(1, 2, 3, 4);
        assertThat(rows).extracting(ConsolidatedRowResponse::getRegionId).containsExactly(1L, 2L, 3L, 4L);
        assertThat(rows.get(0).getRegionNameRu()).isEqualTo("город Астана");
        assertThat(rows.get(0).getRegionNameKz()).isEqualTo("Астана қаласы");
        assertThat(rows).extracting(ConsolidatedRowResponse::getStatus).containsExactly(
                ReportRowStatus.NOT_SUBMITTED, ReportRowStatus.SUBMITTED, ReportRowStatus.DRAFT, ReportRowStatus.NOT_SUBMITTED);

        // Every column is present in every row; only a submitted row carries values
        rows.forEach(row -> assertThat(row.getCells()).containsOnlyKeys("amount", "state"));
        assertThat(rows.get(0).getCells()).containsEntry("amount", null).containsEntry("state", null);
        assertThat(rows.get(1).getCells()).containsEntry("amount", 1500).containsEntry("state", "DONE");
        assertThat(rows.get(2).getCells()).as("drafts are not shown to the admin")
                .containsEntry("amount", null).containsEntry("state", null);
        assertThat(rows.get(1).getSubmittedAt()).isEqualTo(submittedAt);
        assertThat(rows.get(2).getSubmittedAt()).isNull();

        assertThat(table.getTotalRegions()).isEqualTo(4);
        assertThat(table.getSubmittedRegions()).isEqualTo(1);
        assertThat(table.getDraftRegions()).isEqualTo(1);
        assertThat(table.getNotSubmittedRegions()).isEqualTo(2);
        assertThat(table.getGeneratedAt()).isEqualTo(LocalDateTime.of(2026, 9, 28, 1, 30));
    }

    @Test
    void withoutSubmissionsEveryRegionIsNotSubmittedWithEmptyCells() {
        when(templateRepository.findByCodeAndStatus(CODE, TemplateStatus.ACTIVE)).thenReturn(Optional.of(template));
        when(regionDirectory.orderedRegions()).thenReturn(REGIONS);
        when(submissionRepository.findByTemplateCodeAndReportDate(CODE, DATE)).thenReturn(List.of());

        ConsolidatedTableResponse table = service.getConsolidated(CODE, DATE);

        assertThat(table.getRows()).hasSize(4)
                .allSatisfy(row -> {
                    assertThat(row.getStatus()).isEqualTo(ReportRowStatus.NOT_SUBMITTED);
                    assertThat(row.getCells()).containsOnlyKeys("amount", "state");
                    assertThat(row.getCells().values()).containsOnlyNulls();
                });
        assertThat(table.getNotSubmittedRegions()).isEqualTo(4);
        assertThat(table.getSubmittedRegions()).isZero();
        assertThat(table.getDraftRegions()).isZero();
    }

    @Test
    void rowsOfRegionsUnknownToTheDirectoryAreIgnored() {
        when(templateRepository.findByCodeAndStatus(CODE, TemplateStatus.ACTIVE)).thenReturn(Optional.of(template));
        when(regionDirectory.orderedRegions()).thenReturn(REGIONS);
        when(submissionRepository.findByTemplateCodeAndReportDate(CODE, DATE)).thenReturn(List.of(
                submission(99L, SubmissionStatus.SUBMITTED, Map.of("amount", 1, "state", "DONE"), LocalDateTime.now())));

        ConsolidatedTableResponse table = service.getConsolidated(CODE, DATE);

        assertThat(table.getRows()).extracting(ConsolidatedRowResponse::getRegionId).containsExactly(1L, 2L, 3L, 4L);
        assertThat(table.getSubmittedRegions()).isZero();
    }

    @Test
    void withoutDateUsesTodayInAstanaTimeNotUtc() {
        when(templateRepository.findByCodeAndStatus(CODE, TemplateStatus.ACTIVE)).thenReturn(Optional.of(template));
        when(regionDirectory.orderedRegions()).thenReturn(REGIONS);
        when(submissionRepository.findByTemplateCodeAndReportDate(CODE, DATE)).thenReturn(List.of());

        ConsolidatedTableResponse table = service.getConsolidated(CODE, null);

        assertThat(table.getReportDate()).isEqualTo(DATE);
        verify(submissionRepository).findByTemplateCodeAndReportDate(CODE, DATE);
    }

    @Test
    void numericValuesKeepTheirStoredForm() {
        when(templateRepository.findByCodeAndStatus(CODE, TemplateStatus.ACTIVE)).thenReturn(Optional.of(template));
        when(regionDirectory.orderedRegions()).thenReturn(REGIONS);
        when(submissionRepository.findByTemplateCodeAndReportDate(CODE, DATE)).thenReturn(List.of(
                submission(1L, SubmissionStatus.SUBMITTED, Map.of("amount", new BigDecimal("1500.5"), "state", "DONE"),
                        LocalDateTime.now())));

        ConsolidatedTableResponse table = service.getConsolidated(CODE, DATE);

        assertThat(table.getRows().get(0).getCells()).containsEntry("amount", new BigDecimal("1500.5"));
    }

    @Test
    void unknownOrInactiveTemplateIsNotFound() {
        when(templateRepository.findByCodeAndStatus("missing", TemplateStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConsolidated("missing", DATE)).isInstanceOf(NotFoundException.class);
        verify(submissionRepository, never()).findByTemplateCodeAndReportDate(any(), any());
    }

    private static RegionalSubmission submission(Long regionId, SubmissionStatus status, Map<String, Object> cells,
                                                 LocalDateTime submittedAt) {
        return RegionalSubmission.builder()
                .id(regionId * 100).templateCode(CODE).regionId(regionId).reportDate(DATE).status(status)
                .cells(new HashMap<>(cells)).submittedAt(submittedAt).lockVersion(0L)
                .build();
    }
}
