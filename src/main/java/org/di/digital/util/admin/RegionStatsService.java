package org.di.digital.util.admin;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.response.admin.RegionStatsDto;
import org.di.digital.model.enums.appeal.AppealStatus;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.user.AppealRepository;
import org.di.digital.repository.user.RegionRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionStatsService {

    private final RegionRepository regionRepository;
    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final AppealRepository appealRepository;
    private final LocalizationHelper localizationHelper;

    public List<RegionStatsDto> getRegionMapStats(UserSettingsLanguage lang) {
        return regionRepository.findAll().stream()
                .map(region -> RegionStatsDto.builder()
                        .regionId(region.getId())
                        .regionName(localizationHelper.getLocalizedName(region, lang))
                        .mapCode(region.getMapCode())
                        .totalUsers(userRepository.countByRegionId(region.getId()))
                        .activeUsers(userRepository.countByRegionIdAndActiveTrue(region.getId()))
                        .totalCases(caseRepository.countByRegionIdAndStatus(region.getId(), false))
                        .pendingAppeals(appealRepository.countByRegionIdAndStatus(region.getId(), AppealStatus.PENDING))
                        .build())
                .toList();
    }
}
