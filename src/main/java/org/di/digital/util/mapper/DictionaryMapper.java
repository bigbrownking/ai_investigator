package org.di.digital.util.mapper;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.response.*;
import org.di.digital.dto.response.user.AdministrationDto;
import org.di.digital.dto.response.user.ProfessionDto;
import org.di.digital.dto.response.user.RankDto;
import org.di.digital.dto.response.user.RegionDto;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.user.*;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DictionaryMapper {

    private final LocalizationHelper localizationHelper;

    public RegionDto toRegionDto(Region region, UserSettingsLanguage lang) {
        return RegionDto.builder()
                .id(region.getId())
                .name(localizationHelper.getLocalizedName(region, lang))
                .build();
    }

    public AdministrationDto toAdministrationDto(Administration administration, UserSettingsLanguage lang) {
        return AdministrationDto.builder()
                .id(administration.getId())
                .name(localizationHelper.getLocalizedName(administration, lang))
                .build();
    }

    public ProfessionDto toProfessionDto(Profession profession, UserSettingsLanguage lang) {
        return ProfessionDto.builder()
                .id(profession.getId())
                .name(localizationHelper.getLocalizedName(profession, lang))
                .build();
    }

    public RankDto toRankDto(Rank rank, UserSettingsLanguage lang) {
        return RankDto.builder()
                .id(rank.getId())
                .name(localizationHelper.getLocalizedName(rank, lang))
                .build();
    }
}