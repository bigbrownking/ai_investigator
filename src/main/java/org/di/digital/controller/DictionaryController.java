package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.AdministrationDto;
import org.di.digital.dto.response.ProfessionDto;
import org.di.digital.dto.response.RegionDto;
import org.di.digital.model.enums.UserSettingsDetalizationLevel;
import org.di.digital.model.enums.UserSettingsLanguage;
import org.di.digital.model.enums.UserSettingsTheme;
import org.di.digital.repository.AdministrationRepository;
import org.di.digital.repository.ProfessionRepository;
import org.di.digital.repository.RegionRepository;
import org.di.digital.util.Mapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/dict")
public class DictionaryController {
    private final RegionRepository regionRepository;
    private final AdministrationRepository administrationRepository;
    private final ProfessionRepository professionRepository;
    private final Mapper mapper;

    @GetMapping("/languages")
    public ResponseEntity<List<String>> getLanguages() {
        return ResponseEntity.ok(
                Arrays.stream(
                        UserSettingsLanguage.values())
                        .map(UserSettingsLanguage::getLanguage)
                        .collect(Collectors.toList()));
    }

    @GetMapping("/levels")
    public ResponseEntity<List<String>> getLevels() {
        return ResponseEntity.ok(
                Arrays.stream(
                        UserSettingsDetalizationLevel.values())
                        .map(UserSettingsDetalizationLevel::getLevel)
                        .collect(Collectors.toList()));
    }

    @GetMapping("/themes")
    public ResponseEntity<List<String>> getTheme() {
        return ResponseEntity.ok(
                Arrays.stream(
                        UserSettingsTheme.values())
                        .map(UserSettingsTheme::getTheme)
                        .collect(Collectors.toList()));
    }

    @GetMapping("/interrogationRoles")
    public ResponseEntity<List<String>> getInterrogationRoles() {
        return ResponseEntity.ok(
                List.of("Свидетель", "Подозреваемый", "Потерпевший", "Свидетель, имеющий право на защиту", "Специалист", "Эксперт"));
    }

    @GetMapping("interrogationDocumentType")
    public ResponseEntity<List<String>> getInterrogationDocumentTypes(){
        return ResponseEntity.ok(
                List.of("ИИН", "Паспорт"));
    }

    @GetMapping("/regions")
    public ResponseEntity<List<RegionDto>> getRegions() {
        return ResponseEntity.ok(regionRepository.findAll().stream().map(mapper::toRegionDto).collect(Collectors.toList()));
    }

    @GetMapping("/administrations")
    public ResponseEntity<List<AdministrationDto>> getAdministrations() {
        return ResponseEntity.ok(administrationRepository.findAll().stream().map(mapper::toAdministrationDto).collect(Collectors.toList()));
    }

    @GetMapping("/professions")
    public ResponseEntity<List<ProfessionDto>> getProfessions() {
        return ResponseEntity.ok(professionRepository.findAll().stream().map(mapper::toProfessionDto).collect(Collectors.toList()));
    }
}
