package org.di.digital.reporting.mapper;

import lombok.RequiredArgsConstructor;
import org.di.digital.model.user.Region;
import org.di.digital.reporting.dto.response.ConsolidatedRowResponse;
import org.di.digital.reporting.dto.response.OperatorFormResponse;
import org.di.digital.reporting.model.ColumnDefinition;
import org.di.digital.reporting.model.RegionalSubmission;
import org.di.digital.reporting.model.ReportTemplate;
import org.di.digital.reporting.model.enums.ReportRowStatus;
import org.di.digital.reporting.model.enums.SubmissionStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SubmissionMapper {

    private final TemplateMapper templateMapper;

    /**
     * Operator form: the template's columns plus the region's row.
     *
     * @param submission null when nothing was saved for the date
     * @param editable   whether the date is still open for saving
     */
    public OperatorFormResponse toOperatorForm(ReportTemplate template, Long regionId, LocalDate reportDate,
                                               RegionalSubmission submission, boolean editable) {
        OperatorFormResponse.OperatorFormResponseBuilder response = OperatorFormResponse.builder()
                .templateCode(template.getCode())
                .templateName(template.getName())
                .templateDescription(template.getDescription())
                .templateVersion(template.getVersion())
                .columns(templateMapper.toColumnDtos(template.getColumns()))
                .regionId(regionId)
                .reportDate(reportDate)
                .editable(editable);
        if (submission == null) {
            return response.status(ReportRowStatus.NOT_SUBMITTED)
                    .cells(new LinkedHashMap<>())
                    .draftAllowed(editable)
                    .build();
        }
        boolean submitted = submission.getStatus() == SubmissionStatus.SUBMITTED;
        return response
                .status(submitted ? ReportRowStatus.SUBMITTED : ReportRowStatus.DRAFT)
                .cells(currentColumnsOnly(template, submission.getCells()))
                .submittedAt(submission.getSubmittedAt())
                .updatedAt(submission.getUpdatedAt())
                .draftAllowed(editable && !submitted)
                .lockVersion(submission.getLockVersion())
                .build();
    }

    /**
     * One region's row of the consolidated table. Every current column is present;
     * values are shown only for a submitted row, drafts stay hidden from the admin.
     *
     * @param submission null when the region saved nothing for the date
     */
    public ConsolidatedRowResponse toConsolidatedRow(int position, Region region, ReportTemplate template,
                                                     RegionalSubmission submission) {
        boolean submitted = submission != null && submission.getStatus() == SubmissionStatus.SUBMITTED;
        ReportRowStatus status = submission == null ? ReportRowStatus.NOT_SUBMITTED
                : submitted ? ReportRowStatus.SUBMITTED : ReportRowStatus.DRAFT;

        Map<String, Object> cells = new LinkedHashMap<>();
        for (ColumnDefinition column : template.getColumns()) {
            cells.put(column.getKey(), submitted ? submission.getCells().get(column.getKey()) : null);
        }

        return ConsolidatedRowResponse.builder()
                .position(position)
                .regionId(region.getId())
                .regionNameRu(region.getRuName())
                .regionNameKz(region.getKzName())
                .status(status)
                .cells(cells)
                .submittedAt(submitted ? submission.getSubmittedAt() : null)
                .build();
    }

    // A row saved against an older template version may hold keys the current columns no longer have;
    // sending them back would fail with "unknown column", so the form only shows current columns
    private Map<String, Object> currentColumnsOnly(ReportTemplate template, Map<String, Object> cells) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (cells == null) {
            return result;
        }
        for (ColumnDefinition column : template.getColumns()) {
            if (cells.containsKey(column.getKey())) {
                result.put(column.getKey(), cells.get(column.getKey()));
            }
        }
        return result;
    }
}
