package org.di.digital.util.admin;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.response.admin.AdminStatsDto;
import org.di.digital.dto.response.admin.RegionStatsDto;
import org.di.digital.model.enums.appeal.AppealStatus;
import org.di.digital.model.enums.cases.MessageRole;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.repository.cases.CaseAnalyticsRepository;
import org.di.digital.repository.cases.CaseChatMessageRepository;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.indictment.CaseIndictmentRepository;
import org.di.digital.repository.interrogation.CaseInterrogationQARepository;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.repository.qualification.CaseQualificationRepository;
import org.di.digital.repository.user.AppealRepository;
import org.di.digital.repository.user.RegionRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionStatsService {

    private final RegionRepository regionRepository;
    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final AppealRepository appealRepository;
    private final LocalizationHelper localizationHelper;
    private final CaseInterrogationRepository caseInterrogationRepository;
    private final CaseQualificationRepository caseQualificationRepository;
    private final CaseIndictmentRepository caseIndictmentRepository;
    private final CaseFileRepository caseFileRepository;
    private final CaseAnalyticsRepository caseAnalyticsRepository;
    private final CaseChatMessageRepository chatMessageRepository;
    private final CaseInterrogationQARepository caseInterrogationQARepository;

    public List<RegionStatsDto> getRegionMapStats(UserSettingsLanguage lang) {
        return regionRepository.findAll().stream()
                .map(region -> RegionStatsDto.builder()
                        .regionId(region.getId())
                        .regionName(localizationHelper.getLocalizedName(region, lang))
                        .mapCode(region.getMapCode())
                        .totalUsers(userRepository.countByRegionId(region.getId()))
                        .activeUsers(userRepository.countByRegionIdAndActiveTrue(region.getId()))
                        .totalCases(caseRepository.countByRegionIdAndStatus(region.getId(), true))
                        .pendingAppeals(appealRepository.countByRegionIdAndStatus(region.getId(), AppealStatus.PENDING))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminStatsDto getStats(LocalDate from, LocalDate to, Long regionId) {
        LocalDateTime start = from != null ? from.atStartOfDay() : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime end = to != null ? to.plusDays(1).atStartOfDay() : LocalDateTime.now().plusDays(1);

        LocalDate fromDate = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate toDate = to != null ? to : LocalDate.now();

        long totalUsers = userRepository.countByCreatedDateBetweenAndRegion(start, end, regionId);
        long totalCases = caseRepository.countByCreatedDateBetweenAndRegion(start, end, regionId);
        long totalInterrogations = caseInterrogationRepository.countByDateBetweenAndRegion(fromDate, toDate, regionId);

        long totalQualifications = caseQualificationRepository.countNonEmptyBetweenAndRegion(start, end, regionId);
        long totalIndictments = caseIndictmentRepository.countNonEmptyBetweenAndRegion(start, end, regionId);
        long totalAudios = caseInterrogationRepository.countWithAudioBetweenAndRegion(fromDate, toDate, regionId);

        Long totalPagesRaw  = caseFileRepository.countPagesBetweenAndRegion(start, end, regionId);
        long totalPages = totalPagesRaw != null ? totalPagesRaw : 0L;

        Double avgQualificationScore = caseAnalyticsRepository.getAverageQualificationScorePercentBetweenAndRegion(start, end, regionId);

        long totalAiMessages = chatMessageRepository.countByRoleAndInterrogationChatIsNotNullAndCreatedDateBetweenAndRegion(MessageRole.ASSISTANT, start, end, regionId) / 5;
        long totalSelectedMessages = chatMessageRepository.countByIsSelectedTrueAndCreatedDateBetweenAndRegion(start, end, regionId);
        long totalReformulatedMessages = caseInterrogationQARepository.countByIsReformulatedTrueAndCreatedAtBetweenAndRegion(start, end, regionId);

        long qualificationModifier = (regionId == null) ? 193L : 0L;
        long indictmentModifier = (regionId == null) ? 25L : 0L;

        return AdminStatsDto.builder()
                .totalUsers(totalUsers)
                //.activeUsers(activeUsers)
                //.inactiveUsers(inactiveUsers)
                .totalCases(totalCases)
                //.pendingAppeals(pendingAppeals)
                //.approvedAppeals(approvedAppeals)
                //.rejectedAppeals(rejectedAppeals)
                .totalInterrogations(totalInterrogations)
                .totalAudios(totalAudios)
                .totalPages(totalPages)
                .totalQualifications(totalQualifications + qualificationModifier)
                .totalIndictments(totalIndictments + indictmentModifier)
                .totalAiMessages(totalAiMessages)
                .totalSelectedMessages(totalSelectedMessages)
                .totalReformulatedMessages(totalReformulatedMessages)
                .avgQualificationScorePercent(avgQualificationScore)
                .build();
    }
}
