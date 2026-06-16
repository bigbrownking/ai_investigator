package org.di.digital.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.di.digital.service.export.interrogation.InterrogationTemplateService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/interrogations")
@RequiredArgsConstructor
@Tag(name = "Interrogation Preview", description = "Предпросмотр протоколов допроса / Жауап алу хаттамаларының алдын ала қарауы")
public class InterrogationPreviewController {

    private static final Set<String> VALID_ROLES = Set.of(
            "witness", "witness_protected", "suspect", "victim", "specialist", "expert"
    );
    private static final Set<String> VALID_LANGS = Set.of("ru", "kz");
    private static final Set<String> VALID_ARTICLES = Set.of(
            "st64", "st71", "st71_full", "st78", "st65_1", "st80", "st82"
    );

    private final InterrogationTemplateService templateService;

    @GetMapping(
            value = "/preview/{interrogationId}",
            produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8"
    )
    public ResponseEntity<String> previewById(
            @PathVariable Long interrogationId,
            @Parameter(description = "Язык / Тіл: ru | kz")
            @RequestParam(defaultValue = "ru") String lang
    ) {
        if (!VALID_LANGS.contains(lang)) {
            return ResponseEntity.badRequest()
                    .body("Неподдерживаемый язык: " + lang);
        }
        String preview = templateService.buildPreviewById(interrogationId, lang);
        return ResponseEntity.ok(preview);
    }

    @GetMapping(
            value = "/rights",
            produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8"
    )
    public ResponseEntity<String> rights(
            @Parameter(description = "Статья / Баб: st64 | st71 | st71_full | st78 | st65_1 | st80 | st82")
            @RequestParam String article,

            @Parameter(description = "Язык / Тіл: ru | kz")
            @RequestParam(defaultValue = "ru") String lang
    ) {
        if (!VALID_ARTICLES.contains(article)) {
            return ResponseEntity.badRequest()
                    .body("Неизвестная статья: " + article + ". Допустимые: " + VALID_ARTICLES);
        }
        if (!VALID_LANGS.contains(lang)) {
            return ResponseEntity.badRequest()
                    .body("Неподдерживаемый язык: " + lang + ". Доступные: " + VALID_LANGS);
        }

        return ResponseEntity.ok(templateService.getRightsText(article, lang));
    }
}