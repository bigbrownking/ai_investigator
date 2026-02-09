package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.enums.UserSettingsDetalizationLevel;
import org.di.digital.model.enums.UserSettingsLanguage;
import org.di.digital.model.enums.UserSettingsTheme;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/dict")
public class DictionaryController {
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
}
