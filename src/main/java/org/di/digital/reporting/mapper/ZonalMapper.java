package org.di.digital.reporting.mapper;

import lombok.RequiredArgsConstructor;
import org.di.digital.model.user.Region;
import org.di.digital.reporting.dto.request.ZonalColumnRequest;
import org.di.digital.reporting.dto.response.*;
import org.di.digital.reporting.model.ColumnDefinition;
import org.di.digital.reporting.model.SelectOption;
import org.di.digital.reporting.model.TableRow;
import org.di.digital.reporting.model.ZonalSubmission;
import org.di.digital.reporting.model.ZonalTemplate;
import org.di.digital.reporting.model.ZonalWorksheet;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ZonalMapper {

    private final TemplateMapper templateMapper;

    public static String nameKey(String name) {
        return name == null ? null : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public ZonalTemplateSummaryResponse toSummary(ZonalTemplate template) {
        return ZonalTemplateSummaryResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .columnCount(template.getColumns().size())
                .active(template.isActive())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    public ZonalTemplateResponse toResponse(ZonalTemplate template) {
        return ZonalTemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .columns(templateMapper.toColumnDtos(template.getColumns()))
                .active(template.isActive())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .lockVersion(template.getLockVersion())
                .build();
    }

    public ZonalFormSummaryResponse toFormSummary(ZonalTemplate template, ZonalWorksheet worksheet) {
        return ZonalFormSummaryResponse.builder()
                .templateId(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .columnCount(template.getColumns().size())
                .rowCount(worksheet == null ? 0 : worksheet.getRows().size())
                .updatedAt(worksheet == null ? null : worksheet.getUpdatedAt())
                .lastSubmittedAt(worksheet == null ? null : worksheet.getLastSubmittedAt())
                .build();
    }

    public ZonalFormResponse toForm(ZonalTemplate template, Long regionId, ZonalWorksheet worksheet) {
        return ZonalFormResponse.builder()
                .templateId(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .columns(templateMapper.toColumnDtos(template.getColumns()))
                .regionId(regionId)
                .rows(worksheet == null ? new ArrayList<>() : toRowDtos(worksheet.getRows()))
                .updatedAt(worksheet == null ? null : worksheet.getUpdatedAt())
                .lastSubmittedAt(worksheet == null ? null : worksheet.getLastSubmittedAt())
                .lockVersion(worksheet == null ? null : worksheet.getLockVersion())
                .build();
    }

    /** @param region null when the region no longer exists */
    public ZonalSubmissionSummaryResponse toSubmissionSummary(ZonalSubmission submission, Region region) {
        return ZonalSubmissionSummaryResponse.builder()
                .id(submission.getId())
                .templateId(submission.getTemplateId())
                .regionId(submission.getRegionId())
                .regionNameRu(region == null ? null : region.getRuName())
                .regionNameKz(region == null ? null : region.getKzName())
                .name(submission.getName())
                .rowCount(submission.getRowCount())
                .reportDate(submission.getReportDate())
                .submittedAt(submission.getSubmittedAt())
                .submittedBy(submission.getSubmittedBy())
                .comment(submission.getComment())
                .build();
    }

    public ZonalSubmissionResponse toSubmission(ZonalSubmission submission, Region region) {
        return ZonalSubmissionResponse.builder()
                .id(submission.getId())
                .templateId(submission.getTemplateId())
                .regionId(submission.getRegionId())
                .regionNameRu(region == null ? null : region.getRuName())
                .regionNameKz(region == null ? null : region.getKzName())
                .name(submission.getName())
                .columns(templateMapper.toColumnDtos(submission.getColumns()))
                .rows(toRowDtos(submission.getRows()))
                .rowCount(submission.getRowCount())
                .reportDate(submission.getReportDate())
                .submittedAt(submission.getSubmittedAt())
                .submittedBy(submission.getSubmittedBy())
                .comment(submission.getComment())
                .build();
    }

    public List<ZonalRowDto> toRowDtos(List<TableRow> rows) {
        if (rows == null) {
            return new ArrayList<>();
        }
        return rows.stream()
                .map(row -> ZonalRowDto.builder()
                        .id(row.getId())
                        .cells(row.getCells() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(row.getCells()))
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Columns in request order, numbered 1..n; keys must already be filled in by the caller. */
    public List<ColumnDefinition> toColumns(List<ZonalColumnRequest> requests) {
        List<ColumnDefinition> columns = new ArrayList<>();
        for (ZonalColumnRequest request : requests) {
            columns.add(ColumnDefinition.builder()
                    .key(trim(request.getKey()))
                    .label(trim(request.getLabel()))
                    .type(request.getType())
                    .required(request.isRequired())
                    .options(request.getOptions() == null ? new ArrayList<>() : request.getOptions().stream()
                            .filter(o -> o != null)
                            .map(o -> SelectOption.builder().value(trim(o.getValue())).label(trim(o.getLabel())).build())
                            .collect(Collectors.toCollection(ArrayList::new)))
                    .order(columns.size() + 1)
                    .build());
        }
        return columns;
    }

    public List<TableRow> copyRows(List<TableRow> rows) {
        return rows.stream()
                .map(r -> TableRow.builder().id(r.getId()).cells(new LinkedHashMap<>(r.getCells())).build())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
