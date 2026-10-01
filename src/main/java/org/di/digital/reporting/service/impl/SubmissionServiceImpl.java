package org.di.digital.reporting.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.exception.NotFoundException;
import org.di.digital.reporting.config.ReportingClock;
import org.di.digital.reporting.dto.request.ReportRowRequest;
import org.di.digital.reporting.dto.response.OperatorFormResponse;
import org.di.digital.reporting.integration.ReportingUserContext;
import org.di.digital.reporting.mapper.TemplateMapper;
import org.di.digital.reporting.model.ColumnDefinition;
import org.di.digital.reporting.model.RegionalSubmission;
import org.di.digital.reporting.model.ReportTemplate;
import org.di.digital.reporting.model.enums.ReportRowStatus;
import org.di.digital.reporting.model.enums.SubmissionStatus;
import org.di.digital.reporting.model.enums.TemplateStatus;
import org.di.digital.reporting.repository.RegionalSubmissionRepository;
import org.di.digital.reporting.repository.ReportTemplateRepository;
import org.di.digital.reporting.service.SubmissionService;
import org.di.digital.reporting.validation.SubmissionCellsValidator;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A concurrent first save for the same region and date hits the unique constraint,
 * a stale lockVersion hits @Version; both surface as 409.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubmissionServiceImpl implements SubmissionService {

    private final RegionalSubmissionRepository submissionRepository;
    private final ReportTemplateRepository templateRepository;
    private final SubmissionCellsValidator cellsValidator;
    private final TemplateMapper templateMapper;
    private final ReportingUserContext userContext;
    private final ReportingClock clock;

    @Override
    public OperatorFormResponse getForm(String templateCode, LocalDate requestedDate) {
        LocalDate reportDate = requestedDate != null ? requestedDate : clock.today();
        ReportTemplate template = findActiveTemplate(templateCode);
        Long regionId = userContext.currentRegionId();
        RegionalSubmission submission = submissionRepository
                .findByTemplateCodeAndRegionIdAndReportDate(templateCode, regionId, reportDate)
                .orElse(null);
        return toResponse(template, regionId, reportDate, submission);
    }

    @Override
    @Transactional
    public OperatorFormResponse saveDraft(String templateCode, ReportRowRequest request) {
        LocalDate reportDate = request.getReportDate();
        assertEditable(reportDate);
        ReportTemplate template = findActiveTemplate(templateCode);
        Map<String, Object> cells = cellsValidator.validateDraft(template, request.getCells());

        Long regionId = userContext.currentRegionId();
        LocalDateTime now = clock.now();
        RegionalSubmission submission = findOrCreate(templateCode, regionId, reportDate, now);
        checkLockVersion(submission, request.getLockVersion());
        if (submission.getStatus() == SubmissionStatus.SUBMITTED) {
            // A draft would replace validated data with unvalidated data the admin already sees
            throw new IllegalStateException("Report for " + reportDate
                    + " is already submitted; send the corrected row with submit instead of saving a draft");
        }

        submission.setCells(cells);
        submission.setTemplateVersion(template.getVersion());
        submission.setOperatorId(userContext.currentOperatorId());
        submission.setUpdatedAt(now);

        // Flush so the response carries the incremented lockVersion
        return toResponse(template, regionId, reportDate, submissionRepository.saveAndFlush(submission));
    }

    @Override
    @Transactional
    public OperatorFormResponse submit(String templateCode, ReportRowRequest request) {
        LocalDate reportDate = request.getReportDate();
        assertEditable(reportDate);
        ReportTemplate template = findActiveTemplate(templateCode);
        Map<String, Object> cells = cellsValidator.validateSubmit(template, request.getCells());

        Long regionId = userContext.currentRegionId();
        Long operatorId = userContext.currentOperatorId();
        LocalDateTime now = clock.now();
        RegionalSubmission submission = findOrCreate(templateCode, regionId, reportDate, now);
        checkLockVersion(submission, request.getLockVersion());

        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setCells(cells);
        submission.setTemplateVersion(template.getVersion());
        submission.setOperatorId(operatorId);
        submission.setSubmittedAt(now);
        submission.setSubmitCount(submission.getSubmitCount() == null ? 1 : submission.getSubmitCount() + 1);
        submission.setUpdatedAt(now);

        RegionalSubmission saved = submissionRepository.saveAndFlush(submission);
        log.info("Report {} for region {} on {} submitted by operator {} (submission #{}, template v{})",
                templateCode, regionId, reportDate, operatorId, saved.getSubmitCount(), template.getVersion());
        return toResponse(template, regionId, reportDate, saved);
    }

    private void assertEditable(LocalDate reportDate) {
        if (!isEditable(reportDate)) {
            throw new IllegalStateException(
                    "Report for " + reportDate + " can no longer be changed: only today's report ("
                            + clock.today() + ") can be saved or submitted");
        }
    }

    // Single place for the editing window, e.g. to allow late submissions for yesterday
    private boolean isEditable(LocalDate reportDate) {
        return reportDate.equals(clock.today());
    }

    private ReportTemplate findActiveTemplate(String templateCode) {
        return templateRepository.findByCodeAndStatus(templateCode, TemplateStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Report form " + templateCode + " has no active version"));
    }

    private RegionalSubmission findOrCreate(String templateCode, Long regionId, LocalDate reportDate, LocalDateTime now) {
        return submissionRepository.findByTemplateCodeAndRegionIdAndReportDate(templateCode, regionId, reportDate)
                .orElseGet(() -> RegionalSubmission.builder()
                        .templateCode(templateCode)
                        .regionId(regionId)
                        .reportDate(reportDate)
                        .status(SubmissionStatus.DRAFT)
                        .createdAt(now)
                        .build());
    }

    private void checkLockVersion(RegionalSubmission submission, Long lockVersion) {
        // A new row has nothing to conflict with; its first insert is guarded by the unique constraint
        if (lockVersion != null && submission.getId() != null && !lockVersion.equals(submission.getLockVersion())) {
            throw new ObjectOptimisticLockingFailureException(RegionalSubmission.class, submission.getId());
        }
    }

    private OperatorFormResponse toResponse(ReportTemplate template, Long regionId, LocalDate reportDate,
                                            RegionalSubmission submission) {
        boolean editable = isEditable(reportDate);
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
