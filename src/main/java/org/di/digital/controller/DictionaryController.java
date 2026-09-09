package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.dictionary.ModuleDto;
import org.di.digital.dto.response.user.*;
import org.di.digital.model.enums.cases.CaseRejectionReason;
import org.di.digital.model.enums.dictionary.InterrogationDocType;
import org.di.digital.model.enums.dictionary.InterrogationRole;
import org.di.digital.model.enums.dictionary.ModuleType;
import org.di.digital.model.enums.settings.UserSettingsDetalizationLevel;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.enums.settings.UserSettingsTheme;
import org.di.digital.repository.user.*;
import org.di.digital.util.mapper.DictionaryMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.di.digital.util.requests.UserUtil.getCurrentLang;
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
    private final DictionaryMapper mapper;

    private UserSettingsLanguage lang() {
        return getCurrentLang();
    }

    @GetMapping("/languages")
    public ResponseEntity<List<String>> getLanguages() {
        return ResponseEntity.ok(
                Arrays.stream(UserSettingsLanguage.values())
                        .map(v -> v.localized(lang()))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/levels")
    public ResponseEntity<List<String>> getLevels() {
        return ResponseEntity.ok(
                Arrays.stream(UserSettingsDetalizationLevel.values())
                        .map(v -> v.localized(lang()))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/themes")
    public ResponseEntity<List<String>> getTheme() {
        return ResponseEntity.ok(
                Arrays.stream(UserSettingsTheme.values())
                        .map(v -> v.localized(lang()))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/interrogationRoles")
    public ResponseEntity<List<String>> getInterrogationRoles() {
        return ResponseEntity.ok(
                Arrays.stream(InterrogationRole.values())
                        .map(v -> v.localized(lang()))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/interrogationDocumentType")
    public ResponseEntity<List<String>> getInterrogationDocumentTypes() {
        return ResponseEntity.ok(
                Arrays.stream(InterrogationDocType.values())
                        .map(v -> v.localized(lang()))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/modules")
    public ResponseEntity<List<ModuleDto>> getModules() {
        return ResponseEntity.ok(
                Arrays.stream(ModuleType.values())
                        .map(v -> ModuleDto.builder()
                                .code(v.name())
                                .name(v.localized(lang()))
                                .build())
                        .collect(Collectors.toList()));
    }

    @GetMapping("/regions")
    public ResponseEntity<List<RegionDto>> getRegions() {
        return ResponseEntity.ok(
                regionRepository.findAllByOrderByRuNameAsc().stream()
                        .map(r -> mapper.toRegionDto(r, lang()))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/administrations")
    public ResponseEntity<List<AdministrationDto>> getAdministrations() {
        return ResponseEntity.ok(
                administrationRepository.findAll().stream()
                        .map(a -> mapper.toAdministrationDto(a, lang()))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/professions")
    public ResponseEntity<List<ProfessionDto>> getProfessions() {
        return ResponseEntity.ok(
                professionRepository.findAllOrdered().stream()
                        .map(p -> mapper.toProfessionDto(p, lang()))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/professionsAdmin")
    public ResponseEntity<List<ProfessionDto>> getFullProfessions() {
        return ResponseEntity.ok(
                professionRepository.findAllForAdmin().stream()
                        .map(p -> mapper.toProfessionDto(p, lang()))
                        .collect(Collectors.toList()));
    }

    @GetMapping("/ranks")
    public ResponseEntity<List<RankDto>> getRanks() {
        return ResponseEntity.ok(
                rankRepository.findAll().stream()
                        .map(r -> mapper.toRankDto(r, lang()))
                        .collect(Collectors.toList()));
    }
    
    @GetMapping("/rejection-reasons")
    public ResponseEntity<List<String>> getRejectionReasons() {
        return ResponseEntity.ok(
                Arrays.stream(CaseRejectionReason.values())
                        .map(v -> v.localized(lang()))
                        .collect(Collectors.toList()));
    }
}