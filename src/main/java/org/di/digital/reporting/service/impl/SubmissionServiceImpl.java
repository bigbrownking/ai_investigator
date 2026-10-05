package org.di.digital.reporting.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.exception.NotFoundException;
import org.di.digital.reporting.config.ReportingClock;
import org.di.digital.reporting.dto.request.ReportRowRequest;
import org.di.digital.reporting.dto.response.OperatorFormResponse;
import org.di.digital.reporting.mapper.SubmissionMapper;
import org.di.digital.reporting.model.RegionalSubmission;
import org.di.digital.reporting.model.ReportTemplate;
import org.di.digital.reporting.model.enums.SubmissionStatus;
import org.di.digital.reporting.model.enums.TemplateStatus;
import org.di.digital.reporting.repository.RegionalSubmissionRepository;
import org.di.digital.reporting.repository.ReportTemplateRepository;
import org.di.digital.reporting.service.SubmissionService;
import org.di.digital.reporting.validation.SubmissionCellsValidator;
import org.di.digital.util.requests.UserUtil;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

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
    private final SubmissionMapper submissionMapper;
    private final UserUtil userUtil;
    private final ReportingClock clock;

    @Override
    public OperatorFormResponse getForm(String templateCode, LocalDate requestedDate) {
        LocalDate reportDate = requestedDate != null ? requestedDate : clock.today();
        ReportTemplate template = findActiveTemplate(templateCode);
        Long regionId = userUtil.getCurrentUserRegionId();
        RegionalSubmission submission = findRow(templateCode, regionId, reportDate).orElse(null);
        return submissionMapper.toOperatorForm(template, regionId, reportDate, submission, isEditable(reportDate));
    }

    @Override
    @Transactional
    public OperatorFormResponse saveDraft(String templateCode, ReportRowRequest request) {
        LocalDate reportDate = request.getReportDate();
        assertEditable(reportDate);
        ReportTemplate template = findActiveTemplate(templateCode);
        Map<String, Object> cells = cellsValidator.validateDraft(template, request.getCells());

        Long regionId = userUtil.getCurrentUserRegionId();
        LocalDateTime now = clock.now();
        RegionalSubmission submission = findOrCreate(template, regionId, reportDate, now);
        checkLockVersion(submission, request.getLockVersion());
        if (submission.getStatus() == SubmissionStatus.SUBMITTED) {
            // A draft would replace validated data with unvalidated data the admin already sees
            throw new IllegalStateException("Report for " + reportDate
                    + " is already submitted; send the corrected row with submit instead of saving a draft");
        }

        submission.setTemplate(template);
        submission.setCells(cells);
        submission.setOperatorId(currentUserId());
        submission.setUpdatedAt(now);

        // Flush so the response carries the incremented lockVersion
        RegionalSubmission saved = submissionRepository.saveAndFlush(submission);
        return submissionMapper.toOperatorForm(template, regionId, reportDate, saved, true);
    }

    @Override
    @Transactional
    public OperatorFormResponse submit(String templateCode, ReportRowRequest request) {
        LocalDate reportDate = request.getReportDate();
        assertEditable(reportDate);
        ReportTemplate template = findActiveTemplate(templateCode);
        Map<String, Object> cells = cellsValidator.validateSubmit(template, request.getCells());

        Long regionId = userUtil.getCurrentUserRegionId();
        Long operatorId = currentUserId();
        LocalDateTime now = clock.now();
        RegionalSubmission submission = findOrCreate(template, regionId, reportDate, now);
        checkLockVersion(submission, request.getLockVersion());

        submission.setTemplate(template);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setCells(cells);
        submission.setOperatorId(operatorId);
        submission.setSubmittedAt(now);
        submission.setSubmitCount(submission.getSubmitCount() == null ? 1 : submission.getSubmitCount() + 1);
        submission.setUpdatedAt(now);

        RegionalSubmission saved = submissionRepository.saveAndFlush(submission);
        log.info("Report {} for region {} on {} submitted by user {} (submission #{}, template v{})",
                templateCode, regionId, reportDate, operatorId, saved.getSubmitCount(), template.getVersion());
        return submissionMapper.toOperatorForm(template, regionId, reportDate, saved, true);
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

    private Optional<RegionalSubmission> findRow(String templateCode, Long regionId, LocalDate reportDate) {
        return submissionRepository.findFirstByTemplate_CodeAndRegionIdAndReportDateOrderByIdDesc(
                templateCode, regionId, reportDate);
    }

    private RegionalSubmission findOrCreate(ReportTemplate template, Long regionId, LocalDate reportDate,
                                            LocalDateTime now) {
        return findRow(template.getCode(), regionId, reportDate)
                .orElseGet(() -> RegionalSubmission.builder()
                        .template(template)
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

    private static Long currentUserId() {
        return UserUtil.getCurrentUser().getId();
    }
}
