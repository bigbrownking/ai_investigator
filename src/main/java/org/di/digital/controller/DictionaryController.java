package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.dictionary.ModuleDto;
import org.di.digital.dto.response.user.*;
import org.di.digital.model.enums.*;
import org.di.digital.model.enums.dictionary.InterrogationDocType;
import org.di.digital.model.enums.dictionary.InterrogationRole;
import org.di.digital.model.enums.dictionary.ModuleType;
import org.di.digital.repository.user.*;
import org.di.digital.util.Mapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.di.digital.util.requests.UserUtil.getCurrentUser;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/dict")
public class DictionaryController {

    private final RegionRepository regionRepository;
    private final AdministrationRepository administrationRepository;
    private final ProfessionRepository professionRepository;
    private final RankRepository rankRepository;
    private final Mapper mapper;

    private UserSettingsLanguage currentLang() {
        try {
            UserSettingsLanguage l = getCurrentUser().getSettings().getLanguage();
            return l != null ? l : UserSettingsLanguage.KZ;
        } catch (Exception e) {
            return UserSettingsLanguage.KZ;
        }
    }

    @GetMapping("/languages")
    public ResponseEntity<List<String>> getLanguages() {
        UserSettingsLanguage l = currentLang();
        return ResponseEntity.ok(
                Arrays.stream(UserSettingsLanguage.values())
                        .map(v -> v.localized(l))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/levels")
    public ResponseEntity<List<String>> getLevels() {
        UserSettingsLanguage l = currentLang();
        return ResponseEntity.ok(
                Arrays.stream(UserSettingsDetalizationLevel.values())
                        .map(v -> v.localized(l))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/themes")
    public ResponseEntity<List<String>> getTheme() {
        UserSettingsLanguage l = currentLang();
        return ResponseEntity.ok(
                Arrays.stream(UserSettingsTheme.values())
                        .map(v -> v.localized(l))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/interrogationRoles")
    public ResponseEntity<List<String>> getInterrogationRoles() {
        UserSettingsLanguage l = currentLang();
        return ResponseEntity.ok(
                Arrays.stream(InterrogationRole.values())
                        .map(v -> v.localized(l))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/interrogationDocumentType")
    public ResponseEntity<List<String>> getInterrogationDocumentTypes() {
        UserSettingsLanguage l = currentLang();
        return ResponseEntity.ok(
                Arrays.stream(InterrogationDocType.values())
                        .map(v -> v.localized(l))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/modules")
    public ResponseEntity<List<ModuleDto>> getModules() {
        UserSettingsLanguage l = currentLang();
        return ResponseEntity.ok(
                Arrays.stream(ModuleType.values())
                        .map(v -> ModuleDto.builder()
                                .code(v.name())
                                .name(v.localized(l))
                                .build())
                        .collect(Collectors.toList()));
    }

    @GetMapping("/regions")
    public ResponseEntity<List<RegionDto>> getRegions() {
        UserSettingsLanguage l = currentLang();
        return ResponseEntity.ok(
                regionRepository.findAllByOrderByRuNameAsc().stream()
                        .map(r -> mapper.toRegionDto(r, l))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/administrations")
    public ResponseEntity<List<AdministrationDto>> getAdministrations() {
        UserSettingsLanguage l = currentLang();
        return ResponseEntity.ok(
                administrationRepository.findAll().stream()
                        .map(a -> mapper.toAdministrationDto(a, l))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/professions")
    public ResponseEntity<List<ProfessionDto>> getProfessions() {
        UserSettingsLanguage l = currentLang();
        return ResponseEntity.ok(
                professionRepository.findAllOrdered().stream()
                        .map(p -> mapper.toProfessionDto(p, l))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/professionsAdmin")
    public ResponseEntity<List<ProfessionDto>> getFullProfessions() {
        UserSettingsLanguage l = currentLang();
        return ResponseEntity.ok(
                professionRepository.findAllForAdmin().stream()
                        .map(p -> mapper.toProfessionDto(p, l))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/ranks")
    public ResponseEntity<List<RankDto>> getRanks() {
        UserSettingsLanguage l = currentLang();
        return ResponseEntity.ok(
                rankRepository.findAll().stream()
                        .map(r -> mapper.toRankDto(r, l))
                        .collect(Collectors.toList()));
    }
    
    @GetMapping("/rejection-reasons")
    public ResponseEntity<List<String>> getRejectionReasons() {
        UserSettingsLanguage l = currentLang();
        return ResponseEntity.ok(
                Arrays.stream(CaseRejectionReason.values())
                        .map(v -> v.localized(l))
                        .collect(Collectors.toList()));
    }
}