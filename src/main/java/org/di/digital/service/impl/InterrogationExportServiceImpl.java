package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.model.User;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.service.export.InterrogationExportService;
import org.di.digital.service.LogService;
import org.di.digital.service.export.BaseInterrogationDocBuilder;
import org.di.digital.service.export.InterrogationProtocolBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.di.digital.util.UserUtil.getCurrentUser;

/**
 * Оркестратор экспорта протоколов допросов.
 *
 * Маршрутизация по (language × role):
 *
 * language="русском":
 *   witness            → ruWitnessBuilder
 *   victim             → ruVictimBuilder
 *   witness_protected  → ruWitnessProtectedBuilder
 *   suspect            → ruSuspectBuilder
 *   specialist         → ruSpecialistBuilder
 *   expert             → ruExpertBuilder
 *
 * language="казахском":
 *   witness            → kazWitnessBuilder
 *   victim             → kazVictimBuilder
 *   witness_protected  → kazWitnessProtectedBuilder
 *   suspect            → kazSuspectBuilder
 *   specialist         → kazSpecialistBuilder
 *   expert             → kazExpertBuilder
 *
 * Флаг isDop обрабатывается внутри каждого билдера.
 */
@Service
@RequiredArgsConstructor
public class InterrogationExportServiceImpl implements InterrogationExportService {

    private final LogService logService;

    // ─── RU билдеры ──────────────────────────────────────────────────────────
    private final InterrogationProtocolBuilder ruWitnessBuilder;
    private final InterrogationProtocolBuilder ruVictimBuilder;
    private final InterrogationProtocolBuilder ruWitnessProtectedBuilder;
    private final InterrogationProtocolBuilder ruSuspectBuilder;
    private final InterrogationProtocolBuilder ruSpecialistBuilder;
    private final InterrogationProtocolBuilder ruExpertBuilder;

    // ─── KAZ билдеры ─────────────────────────────────────────────────────────
    private final InterrogationProtocolBuilder kazWitnessBuilder;
    private final InterrogationProtocolBuilder kazVictimBuilder;
    private final InterrogationProtocolBuilder kazWitnessProtectedBuilder;
    private final InterrogationProtocolBuilder kazSuspectBuilder;
    private final InterrogationProtocolBuilder kazSpecialistBuilder;
    private final InterrogationProtocolBuilder kazExpertBuilder;

    // ─── Маппинг: язык → (роль → билдер) ────────────────────────────────────
    private Map<String, InterrogationProtocolBuilder> ruBuilders() {
        return Map.of(
                "witness",           ruWitnessBuilder,
                "victim",            ruVictimBuilder,
                "witness_protected", ruWitnessProtectedBuilder,
                "suspect",           ruSuspectBuilder,
                "specialist",        ruSpecialistBuilder,
                "expert",            ruExpertBuilder
        );
    }

    private Map<String, InterrogationProtocolBuilder> kazBuilders() {
        return Map.of(
                "witness",           kazWitnessBuilder,
                "victim",            kazVictimBuilder,
                "witness_protected", kazWitnessProtectedBuilder,
                "suspect",           kazSuspectBuilder,
                "specialist",        kazSpecialistBuilder,
                "expert",            kazExpertBuilder
        );
    }

    // ─── Entry point ─────────────────────────────────────────────────────────

    @Override
    public byte[] exportToDocx(CaseInterrogationFullResponse data) {
        User currentUser = getCurrentUser();
        String role     = data.getRole() != null ? data.getRole().trim().toLowerCase() : "witness";
        String language = data.getLanguage() != null ? data.getLanguage().trim() : "русском";

        InterrogationProtocolBuilder builder = resolveBuilder(language, role);

        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            BaseInterrogationDocBuilder.setPageMargins(doc);
            builder.build(doc, data);
            doc.write(out);

            logService.log(
                    String.format("Downloading interrogation %s by %s user in case %s",
                            data.getProtocol().getInterrogationId(),
                            currentUser.getEmail(),
                            data.getCaseNumber()),
                    LogLevel.INFO,
                    LogAction.INTERROGATION_DOWNLOAD,
                    data.getCaseNumber(),
                    currentUser.getEmail()
            );

            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Ошибка при генерации DOCX", e);
        }
    }

    // ─── Маршрутизация ───────────────────────────────────────────────────────

    private InterrogationProtocolBuilder resolveBuilder(String language, String role) {
        Map<String, InterrogationProtocolBuilder> builders =
                "казахском".equals(language) ? kazBuilders() : ruBuilders();

        InterrogationProtocolBuilder builder = builders.get(role);
        if (builder == null) {
            // По умолчанию — свидетель
            builder = builders.get("witness");
        }
        return builder;
    }
}