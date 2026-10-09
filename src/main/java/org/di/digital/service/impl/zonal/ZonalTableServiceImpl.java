package org.di.digital.service.impl.zonal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.zonal.*;
import org.di.digital.exception.NotFoundException;
import org.di.digital.model.user.Region;
import org.di.digital.model.user.User;
import org.di.digital.config.ReportingClock;
import org.di.digital.dto.request.zonal.ZonalRowsRequest;
import org.di.digital.util.mapper.TemplateMapper;
import org.di.digital.util.mapper.ZonalMapper;
import org.di.digital.model.zonal.TableRow;
import org.di.digital.model.zonal.ZonalSubmission;
import org.di.digital.model.zonal.ZonalTemplate;
import org.di.digital.model.zonal.ZonalWorksheet;
import org.di.digital.repository.zonal.ZonalSubmissionRepository;
import org.di.digital.repository.zonal.ZonalTemplateRepository;
import org.di.digital.repository.zonal.ZonalWorksheetRepository;
import org.di.digital.service.zonal.ZonalTableService;
import org.di.digital.exception.ReportingValidationException;
import org.di.digital.exception.SubmissionCellsValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.di.digital.util.requests.UserUtil.getCurrentUser;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZonalTableServiceImpl implements ZonalTableService {

    static final int MAX_ROWS = 5000;
    static final int MAX_PAGE_SIZE = 100;
    private static final Pattern ROW_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final ZonalTemplateRepository templateRepository;
    private final ZonalWorksheetRepository worksheetRepository;
    private final ZonalSubmissionRepository submissionRepository;
    private final SubmissionCellsValidator cellsValidator;
    private final TemplateMapper templateMapper;
    private final ZonalMapper zonalMapper;
    private final ReportingClock clock;

    @Override
    public List<ZonalFormSummaryResponse> listForms() {
        Long regionId = currentRegion().getId();
        Map<Long, ZonalWorksheet> worksheets = worksheetRepository.findByRegionId(regionId).stream()
                .collect(Collectors.toMap(ZonalWorksheet::getTemplateId, Function.identity()));
        return templateRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(t -> zonalMapper.toFormSummary(t, worksheets.get(t.getId())))
                .toList();
    }

    @Override
    public ZonalFormResponse getForm(Long templateId) {
        ZonalTemplate template = findActive(templateId);
        Long regionId = currentRegion().getId();
        ZonalWorksheet worksheet = worksheetRepository.findByTemplateIdAndRegionId(templateId, regionId).orElse(null);
        return zonalMapper.toForm(template, regionId, worksheet);
    }

    @Override
    @Transactional
    public ZonalFormResponse saveRows(Long templateId, ZonalRowsRequest request) {
        ZonalTemplate template = findActive(templateId);
        Long regionId = currentRegion().getId();
        ZonalWorksheet worksheet = findOrCreate(templateId, regionId);
        checkLockVersion(worksheet, request.getLockVersion());

        applyDraftRows(template, worksheet, request.getRows() == null ? List.of() : request.getRows());
        return zonalMapper.toForm(template, regionId, worksheetRepository.saveAndFlush(worksheet));
    }

    @Override
    @Transactional
    public ZonalSubmissionResponse submit(Long templateId, ZonalRowsRequest request) {
        ZonalTemplate template = findActive(templateId);
        Region region = currentRegion();
        ZonalWorksheet worksheet = findOrCreate(templateId, region.getId());
        checkLockVersion(worksheet, request.getLockVersion());
        if (request.getRows() != null) {
            applyDraftRows(template, worksheet, request.getRows());
        }

        List<TableRow> validated = validateForSubmit(template, worksheet.getRows());
        LocalDateTime now = clock.now();
        Long userId = currentUserId();

        ZonalSubmission submission = submissionRepository.save(ZonalSubmission.builder()
                .templateId(template.getId())
                .regionId(region.getId())
                .name(template.getName())
                .columns(templateMapper.copyColumns(template.getColumns()))
                .rows(validated)
                .rowCount(validated.size())
                .reportDate(clock.today())
                .submittedAt(now)
                .submittedBy(userId)
                .comment(request.getComment() == null || request.getComment().isBlank() ? null : request.getComment().trim())
                .build());

        worksheet.setLastSubmittedAt(now);
        if (worksheet.getUpdatedAt() == null) {
            worksheet.setUpdatedAt(now);
            worksheet.setUpdatedBy(userId);
        }
        ZonalWorksheet savedWorksheet = worksheetRepository.saveAndFlush(worksheet);

        log.info("Zonal template {} '{}' sent by region {} (user {}): {} rows, submission {}",
                template.getId(), template.getName(), region.getId(), userId, validated.size(), submission.getId());
        ZonalSubmissionResponse response = zonalMapper.toSubmission(submission, region);
        response.setFormLockVersion(savedWorksheet.getLockVersion());
        return response;
    }

    @Override
    public Page<ZonalSubmissionSummaryResponse> getHistory(Long templateId, int page, int size) {
        Region region = currentRegion();
        Specification<ZonalSubmission> spec = (root, query, cb) -> cb.equal(root.get("regionId"), region.getId());
        if (templateId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("templateId"), templateId));
        }
        return submissionRepository.findAll(spec, pageRequest(page, size))
                .map(s -> zonalMapper.toSubmissionSummary(s, region));
    }

    @Override
    public ZonalSubmissionResponse getSubmission(Long submissionId) {
        Region region = currentRegion();
        ZonalSubmission submission = submissionRepository.findByIdAndRegionId(submissionId, region.getId())
                .orElseThrow(() -> new NotFoundException("Sent table not found: " + submissionId));
        return zonalMapper.toSubmission(submission, region);
    }

    static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "submittedAt", "id"));
    }

    private void applyDraftRows(ZonalTemplate template, ZonalWorksheet worksheet, List<ZonalRowDto> input) {
        if (input.size() > MAX_ROWS) {
            throw new ReportingValidationException(List.of("A table can have at most " + MAX_ROWS + " rows"));
        }
        RowErrors errors = new RowErrors();
        Set<String> ids = new HashSet<>();
        List<TableRow> rows = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ZonalRowDto dto = input.get(i);
            if (dto == null) {
                continue;
            }
            String id = dto.getId() != null && ROW_ID.matcher(dto.getId()).matches() && ids.add(dto.getId())
                    ? dto.getId() : newRowId(ids);
            try {
                rows.add(TableRow.builder()
                        .id(id)
                        .cells(cellsValidator.validateDraft(template.getColumns(), dto.getCells()))
                        .build());
            } catch (ReportingValidationException e) {
                errors.add(i, id, e);
            }
        }
        errors.throwIfAny();

        worksheet.setRows(rows);
        worksheet.setUpdatedAt(clock.now());
        worksheet.setUpdatedBy(currentUserId());
    }

    private List<TableRow> validateForSubmit(ZonalTemplate template, List<TableRow> rows) {
        RowErrors errors = new RowErrors();
        List<TableRow> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            TableRow row = rows.get(i);
            if (isEmpty(row)) {
                continue;
            }
            try {
                Map<String, Object> cells = cellsValidator.validateSubmit(template.getColumns(), row.getCells());
                result.add(TableRow.builder().id(row.getId()).cells(cells).build());
            } catch (ReportingValidationException e) {
                errors.add(i, row.getId(), e);
            }
        }
        errors.throwIfAny();
        return result;
    }

    private static boolean isEmpty(TableRow row) {
        return row.getCells() == null || row.getCells().values().stream()
                .allMatch(v -> v == null || (v instanceof String s && s.isBlank()));
    }

    private static String newRowId(Set<String> used) {
        String id;
        do {
            id = UUID.randomUUID().toString();
        } while (!used.add(id));
        return id;
    }

    private ZonalTemplate findActive(Long templateId) {
        return templateRepository.findByIdAndActiveTrue(templateId)
                .orElseThrow(() -> new NotFoundException("Template not found or not active: " + templateId));
    }

    private ZonalWorksheet findOrCreate(Long templateId, Long regionId) {
        return worksheetRepository.findByTemplateIdAndRegionId(templateId, regionId)
                .orElseGet(() -> ZonalWorksheet.builder()
                        .templateId(templateId)
                        .regionId(regionId)
                        .rows(new ArrayList<>())
                        .createdAt(clock.now())
                        .build());
    }

    private void checkLockVersion(ZonalWorksheet worksheet, Long lockVersion) {
        if (lockVersion != null && worksheet.getId() != null && !lockVersion.equals(worksheet.getLockVersion())) {
            throw new ObjectOptimisticLockingFailureException(ZonalWorksheet.class, worksheet.getId());
        }
    }

    private static Region currentRegion() {
        User user = getCurrentUser();
        if (user.getRegion() == null) {
            throw new IllegalStateException("No region is assigned to the current user");
        }
        return user.getRegion();
    }

    private static Long currentUserId() {
        return getCurrentUser().getId();
    }

    private static final class RowErrors {
        private final List<String> flat = new ArrayList<>();
        private final Map<String, List<String>> byCell = new LinkedHashMap<>();

        void add(int index, String rowId, ReportingValidationException e) {
            e.getErrors().forEach(message -> flat.add("Row " + (index + 1) + ", " + message));
            e.getCellErrors().forEach((columnKey, messages) ->
                    byCell.computeIfAbsent(rowId + "." + columnKey, k -> new ArrayList<>()).addAll(messages));
        }

        void throwIfAny() {
            if (!flat.isEmpty()) {
                throw new ReportingValidationException(flat, byCell);
            }
        }
    }
}
