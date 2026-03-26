package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.*;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.dto.response.CaseInterrogationProtocolResponse;
import org.di.digital.dto.response.CaseInterrogationQAResponse;
import org.di.digital.dto.response.InterrogationTimerSessionResponse;
import org.di.digital.model.User;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.service.InterrogationExportService;
import org.di.digital.service.LogService;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static org.di.digital.util.UserUtil.getCurrentUser;

/**
 * Поддерживаемые виды допросов (поле role + isDop):
 *
 *  role="witness"  + isDop=false → Протокол допроса свидетеля
 *  role="witness"  + isDop=true  → Протокол дополнительного допроса свидетеля
 *  role="victim"   + isDop=false → Протокол допроса потерпевшего
 *  role="victim"   + isDop=true  → Протокол дополнительного допроса потерпевшего
 *  role="witness_protected" + isDop=false → Протокол допроса свидетеля, имеющего право на защиту
 *  role="witness_protected" + isDop=true  → Протокол доп. допроса свидетеля, имеющего право на защиту
 *  role="specialist"              → Протокол о допросе специалиста
 *  role="specialist" + isDop=true  → Протокол о дополнительном допросе специалиста
 *  role="expert"                   → Протокол о допросе эксперта
 *  role="expert"     + isDop=true  → Протокол о дополнительном допросе эксперта
 *  role="suspect"  + isDop=false → Протокол допроса подозреваемого
 *  role="suspect"  + isDop=true  → Протокол дополнительного допроса подозреваемого
 */
@Service
@RequiredArgsConstructor
public class InterrogationExportServiceImpl implements InterrogationExportService {

    private static final DateTimeFormatter DATE_RU =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'года'", new Locale("ru"));
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH 'час.' mm 'мин.'");

    private final LogService logService;
    private User currentUser;

    // ─── Entry point ──────────────────────────────────────────────────────────

    public byte[] exportToDocx(CaseInterrogationFullResponse data) {
        currentUser = getCurrentUser();
        String role = data.getRole() != null ? data.getRole().trim().toLowerCase() : "witness";
        boolean isDop = Boolean.TRUE.equals(data.getIsDop());

        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            setPageMargins(doc);

            switch (role) {
                case "specialist"        -> { if (isDop) buildDopSpecialist(doc, data);
                else       buildSpecialist(doc, data); }
                case "expert"            -> { if (isDop) buildDopExpert(doc, data);
                else       buildExpert(doc, data); }
                case "witness_protected" -> { if (isDop) buildDopWitnessProtected(doc, data);
                else       buildWitnessProtected(doc, data); }
                case "victim"            -> { if (isDop) buildDopVictim(doc, data);
                else       buildVictim(doc, data); }
                case "suspect"           -> { if (isDop) buildDopSuspect(doc, data);
                else       buildSuspect(doc, data); }
                default /* witness */    -> { if (isDop) buildDopWitness(doc, data);
                else       buildWitness(doc, data); }
            }

            doc.write(out);
            String caseNumber = data.getCaseNumber();
            String email = currentUser.getEmail();
            logService.log(
                    String.format("Downloading interrogation %s by %s user in case %s", data.getProtocol().getInterrogationId(),email, caseNumber),
                    LogLevel.INFO,
                    LogAction.INTERROGATION_DOWNLOAD,
                    caseNumber,
                    email
            );
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при генерации DOCX", e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. ДОПРОС СВИДЕТЕЛЯ (обычный)
    // ══════════════════════════════════════════════════════════════════════════

    private void buildWitness(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String fio = safe(data.getFio());
        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "допроса свидетеля", 12);
        addEmptyLine(doc);
        addCityDateBlock(doc, data);
        addEmptyLine(doc);

        // Вводный блок
        addJustifiedParagraph(doc,
                currentUser.getProfession() + " по " + currentUser.getRegion() +
                        " в помещении кабинета " + safe(data.getRoom()) +
                        " административного здания, расположенного по адресу: " + safe(data.getAddrezz()) +
                        ", с соблюдением требований ст.ст.60, 78, 110, 115, 197, 199 ч.3, 208, 209, 210, 212, 214 УПК РК" +
                        " допросил по уголовному делу №" + safe(data.getCaseNumber()) +
                        " в качестве свидетеля:");
        addEmptyLine(doc);
        addPersonDataTable(doc, data);
        addEmptyLine(doc);

        addInvolvedBlock(doc, data);
        addJustifiedParagraph(doc, "Перед началом следственного действия участникам объявлен порядок производства допроса.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Свидетелю " + fio + " сообщено, что он(а) будет допрошен(а) по уголовному делу №" + safe(data.getCaseNumber()) + ".");
        addEmptyLine(doc);

        // Права ст.78
        addRightsSection(doc, "witness", fio);

        // Предупреждение об ответственности
        addJustifiedParagraph(doc, "Свидетель " + fio +
                " предупрежден(а) об уголовной ответственности за заведомо ложные показания " +
                "и за отказ от дачи показаний, предусмотренными ст.ст.420, 421 УК РК.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Свидетель", fio);
        addEmptyLine(doc);

        addTestimonyIntroWitness(doc, data, "Свидетель", fio);
        addQABlock(doc, data, "Свидетель", fio);
        addClosingSection(doc, data, "свидетеля", "Свидетель", fio);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. ДОПОЛНИТЕЛЬНЫЙ ДОПРОС СВИДЕТЕЛЯ
    // ══════════════════════════════════════════════════════════════════════════

    private void buildDopWitness(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String fio = safe(data.getFio());
        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "дополнительного допроса свидетеля", 12);
        addEmptyLine(doc);
        addCityDateBlockExtended(doc, data); // с перерывами
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                currentUser.getProfession() + " по " + currentUser.getRegion() +
                        " в помещении кабинета " + safe(data.getRoom()) +
                        " административного здания, расположенного по адресу: " + safe(data.getAddrezz()) +
                        ", с соблюдением требований ст.ст.78, 115, 126, 197, 199, 208-212, 214 УПК РК" +
                        ", дополнительно допросил по уголовному делу №" + safe(data.getCaseNumber()) +
                        " в качестве свидетеля " + fio + ".");
        addEmptyLine(doc);

        // Привлечённые лица
        addInvolvedSpecialistBlock(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Перед началом следственного действия участникам объявлен порядок производства дополнительного допроса.");
        addJustifiedParagraph(doc, "Участники уведомлены о применении научно-технических средств: " +
                safe(data.getInvolved()) + ".");
        addEmptyLine(doc);

        addInlineSignatureLine(doc, "Свидетель", fio);
        addInlineSignatureLine(doc, "Специалист(ы)", "");
        addInlineSignatureLine(doc, "Переводчик", "");
        addEmptyLine(doc);

        addSpecialistTranslatorRights(doc);

        addJustifiedParagraph(doc,
                "Перед началом следственного действия свидетелю " + fio +
                        " сообщено, что он(а) будет дополнительно допрошен(а) по уголовному делу №" +
                        safe(data.getCaseNumber()) + ".");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Свидетель " + fio +
                        " предупрежден(а) об уголовной ответственности за заведомо ложные показания " +
                        "и за отказ от дачи показаний, предусмотренными ст.ст.420, 421 УК РК.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Свидетель", fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Свидетелю разъяснено право отказаться от дачи показаний, уличающих в совершении " +
                        "уголовного правонарушения его самого, супруга (супруги), близких родственников.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Свидетель", fio);
        addEmptyLine(doc);

        addLanguageBlock(doc, data, "Свидетель", fio);

        addJustifiedParagraph(doc,
                "Свидетелю предложено рассказать об отношениях, в которых он состоит с подозреваемым(ой), " +
                        "а также по существу известных ему обстоятельств, имеющих значение для дела, " +
                        "на что свидетель " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);

        addQABlock(doc, data, "Свидетель", fio);
        addClosingSectionMultiParty(doc, data, "дополнительного допроса свидетеля", "Свидетель", fio);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. ДОПРОС ПОТЕРПЕВШЕГО
    // ══════════════════════════════════════════════════════════════════════════

    private void buildVictim(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String fio = safe(data.getFio());
        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "допроса потерпевшего", 12);
        addEmptyLine(doc);
        addCityDateBlock(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                currentUser.getProfession() + " по " + currentUser.getRegion() +
                        " в помещении кабинета " + safe(data.getRoom()) +
                        " административного здания, расположенного по адресу: " + safe(data.getAddrezz()) +
                        ", с соблюдением требований ст.ст.71, 80, 81, 110, 115, 197, 199, 208–210, 212, 214 УПК РК" +
                        " допросил по уголовному делу №" + safe(data.getCaseNumber()) +
                        " в качестве потерпевшего(ей):");
        addEmptyLine(doc);
        addPersonDataTable(doc, data);
        addEmptyLine(doc);

        addInvolvedBlock(doc, data);
        addJustifiedParagraph(doc, "Перед началом следственного действия участникам объявлен порядок производства допроса.");
        addEmptyLine(doc);

        // Права ст.71
        addRightsSection(doc, "victim", fio);

        // Предупреждение
        addJustifiedParagraph(doc,
                "Потерпевший(ая) " + fio +
                        " предупрежден(а): об уголовной ответственности по ст.419 УК РК за заведомо ложный донос, " +
                        "об уголовной ответственности по ст.420 УК РК за дачу заведомо ложных показаний; " +
                        "об уголовной ответственности по ст.421 УК РК за отказ или уклонение от дачи показаний.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Потерпевший(ая)", fio);
        addEmptyLine(doc);

        addTestimonyIntroVictim(doc, data, fio);
        addQABlock(doc, data, "Потерпевший(ая)", fio);
        addClosingSection(doc, data, "потерпевшего", "Потерпевший(ая)", fio);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. ДОПОЛНИТЕЛЬНЫЙ ДОПРОС ПОТЕРПЕВШЕГО
    // ══════════════════════════════════════════════════════════════════════════

    private void buildDopVictim(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String fio = safe(data.getFio());
        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "дополнительного допроса потерпевшего", 12);
        addEmptyLine(doc);
        addCityDateBlock(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                currentUser.getProfession() + " по " + currentUser.getRegion() +
                        " в помещении кабинета " + safe(data.getRoom()) +
                        " административного здания, расположенного по адресу: " + safe(data.getAddrezz()) +
                        ", с соблюдением требований ст.ст.71, 110, 115, 197, 199, 209-212, 214 УПК РК" +
                        " дополнительно допросил в качестве потерпевшего(ей)" +
                        " по материалам досудебного расследования №" + safe(data.getCaseNumber()) + ":");
        addEmptyLine(doc);
        addPersonDataTableVictimDop(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Перед началом допроса в соответствии со ст.ст.110, 214 УПК РК потерпевшему разъяснены " +
                        "процессуальные права и обязанности потерпевшего, в том числе права не свидетельствовать " +
                        "против самого себя, супруга (супруги) и своих близких родственников, круг которых определен законом. " +
                        "Одновременно разъяснено, что в соответствии со ст.71 УПК РК:");
        addEmptyLine(doc);

        addJustifiedParagraph(doc, RIGHTS_ST71_FULL);
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Потерпевший(ая)", fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Потерпевший(ая) " + fio +
                        " предупрежден(а): об уголовной ответственности по ст.419 УК РК за заведомо ложный донос, " +
                        "по ст.420 УК РК за дачу заведомо ложных показаний; по ст.421 УК РК за отказ или уклонение от дачи показаний.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Потерпевший(ая)", fio);
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc,
                "После чего, потерпевший(ая) " + fio + " заявил(а):");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc,
                "Права потерпевшего, предусмотренные ст.71 УПК РК, мне разъяснены, от дачи показаний я не отказываюсь, " +
                        "желаю давать показания на " + safe(data.getLanguage()) + " языке, которым владею свободно, " +
                        "в услугах переводчика не нуждаюсь.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Потерпевший(ая)", fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "По существу заданных вопросов могу показать следующее:");
        addEmptyLine(doc);

        addQABlock(doc, data, "Потерпевший(ая)", fio);

        addJustifiedParagraph(doc, "С целью уточнения и дополнения показаний потерпевшего, ему(ей) заданы следующие вопросы:");
        addEmptyLine(doc);
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc, "Более мне пояснить нечего.");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc, "С моих слов напечатано верно и мною прочитано.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Потерпевший(ая)", fio);
        addEmptyLine(doc);

        addInvestigatorBlock(doc, data);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. ДОПРОС СВИДЕТЕЛЯ, ИМЕЮЩЕГО ПРАВО НА ЗАЩИТУ
    // ══════════════════════════════════════════════════════════════════════════

    private void buildWitnessProtected(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String fio = safe(data.getFio());
        String roleLabel = "Свидетель, имеющий право на защиту";
        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "допроса свидетеля, имеющего право на защиту", 12);
        addEmptyLine(doc);
        addCityDateBlockExtended(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                currentUser.getProfession() + " по " + currentUser.getRegion() +
                        " в помещении кабинета " + safe(data.getRoom()) +
                        " административного здания, расположенного по адресу: " + safe(data.getAddrezz()) +
                        ", с соблюдением требований ст.ст.65-1, 80, 81, 110, 115, 197, 199, 208-210, 212, 214 УПК РК" +
                        ", с участием защитника, представившего уведомление №" + safe(data.getNotificationNumber()) +
                        " от " + safe(data.getNotificationDate()) +
                        ", допросил по уголовному делу №" + safe(data.getCaseNumber()) +
                        " в качестве свидетеля, имеющего право на защиту:");
        addEmptyLine(doc);
        addPersonDataTable(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Перед началом следственного действия участникам объявлен порядок производства допроса.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Перед началом следственного действия участникам сообщено, что " + fio +
                        " будет допрошен(а) по уголовному делу №" + safe(data.getCaseNumber()) + ".");
        addEmptyLine(doc);

        // Права ст.65-1
        addJustifiedParagraph(doc,
                "Свидетелю, имеющему право на защиту, разъяснены права и обязанности, предусмотренные ст.65-1 УПК РК, а именно:");
        addEmptyLine(doc);
        addJustifiedParagraph(doc, RIGHTS_ST65_1);
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc,
                "Права и обязанности свидетеля, имеющего право на защиту, предусмотренные ст.65-1 УПК РК, мне разъяснены. Сущность прав ясна.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Свидетелю, имеющему право на защиту, разъяснено право отказаться от дачи показаний, " +
                        "уличающих в совершении уголовного правонарушения его самого, супруга (супруги), близких родственников.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addLanguageBlock(doc, data, roleLabel, fio);

        addJustifiedParagraph(doc,
                "Свидетелю, имеющему право на защиту, предложено рассказать об отношениях, в которых он состоит " +
                        "с подозреваемым(ой), а также по существу известных ему обстоятельств, имеющих значение для дела, " +
                        "на что " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);

        addQABlock(doc, data, roleLabel, fio);

        addJustifiedParagraph(doc,
                "На этом допрос свидетеля, имеющего право на защиту, окончен. " +
                        "Участникам допроса разъяснено право ознакомиться с результатами допроса, " +
                        "а также право внесения в протокол заявлений, замечаний, дополнений, уточнений и исправлений.");
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc,
                "Протокол допроса записан правильно. Заявлений, замечаний, дополнений, уточнений и исправлений, " +
                        "подлежащих внесению в протокол, не имею(ем).");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addInvestigatorBlock(doc, data);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. ДОПОЛНИТЕЛЬНЫЙ ДОПРОС СВИДЕТЕЛЯ, ИМЕЮЩЕГО ПРАВО НА ЗАЩИТУ
    // ══════════════════════════════════════════════════════════════════════════

    private void buildDopWitnessProtected(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String fio = safe(data.getFio());
        String roleLabel = "Свидетель, имеющий право на защиту";
        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "дополнительного допроса свидетеля, имеющего право на защиту", 12);
        addEmptyLine(doc);
        addCityDateBlockExtended(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                currentUser.getProfession() + " по " + currentUser.getRegion() +
                        " в помещении кабинета " + safe(data.getRoom()) +
                        " административного здания, расположенного по адресу: " + safe(data.getAddrezz()) +
                        ", с соблюдением требований ст.ст.65-1, 80, 81, 110, 115, 197, 199, 208-212, 214 УПК РК" +
                        ", дополнительно допросил по уголовному делу №" + safe(data.getCaseNumber()) +
                        " в качестве свидетеля, имеющего право на защиту " + fio + ".");
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Перед началом следственного действия участникам объявлен порядок производства дополнительного допроса.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Перед началом следственного действия, свидетелю, имеющему право на защиту " + fio +
                        " сообщено, что он(а) будет дополнительно допрошен(а) по уголовному делу №" + safe(data.getCaseNumber()) + ".");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Свидетелю, имеющему право на защиту, разъяснено право отказаться от дачи показаний, " +
                        "уличающих в совершении уголовного правонарушения его(ее) самого(ой), супруга (супруги), близких родственников.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addLanguageBlock(doc, data, roleLabel, fio);

        addJustifiedParagraph(doc,
                "Свидетелю, имеющему право на защиту, предложено рассказать о дополнениях или уточнениях ранее данных " +
                        "показаний по обстоятельствам расследуемого дела в силу их недостаточной ясности или неполноты, " +
                        "о которых он изъявил желание сообщить, либо о возникших существенных для дела новых вопросах, " +
                        "на что свидетель, имеющий право на защиту " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);

        addQABlock(doc, data, roleLabel, fio);

        addJustifiedParagraph(doc,
                "На этом дополнительный допрос свидетеля, имеющего право на защиту, окончен. " +
                        "Участникам допроса разъяснено право ознакомиться с результатами допроса, " +
                        "а также право внесения в протокол заявлений, замечаний, дополнений, уточнений и исправлений.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Протокол дополнительного допроса свидетеля, имеющего право на защиту предъявлен для ознакомления. " +
                "С настоящим протоколом допроса ознакомлен(а) путем личного прочтения, оглашения по просьбе участника.");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc, "Протокол дополнительного допроса записан правильно. Заявлений, замечаний, дополнений, уточнений и исправлений, подлежащих внесению в протокол, не имею(ем).");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addInvestigatorBlock(doc, data);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. ДОПРОС СПЕЦИАЛИСТА
    // ══════════════════════════════════════════════════════════════════════════

    private void buildSpecialist(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String fio = safe(data.getFio());
        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "о допросе специалиста", 12);
        addEmptyLine(doc);
        addCityDateBlockSimple(doc, data); // Начало/Окончание без перерывов
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                currentUser.getProfession() + " по " + currentUser.getRegion() +
                        " в помещении кабинета " + safe(data.getRoom()) +
                        " административного здания, расположенного по адресу: " + safe(data.getAddrezz()) +
                        ", с соблюдением требований ст.ст.80, 197, 199, 208-212, 285 УПК РК" +
                        ", допросил по уголовному делу №" + safe(data.getCaseNumber()) +
                        " в качестве специалиста " + fio + ".");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Перед началом следственного действия специалисту " + fio +
                        " сообщено, что он(а) будет допрошен(а) по обстоятельствам, связанным с ранее данным им заключением, " +
                        "а также ему объявлен порядок производства допроса.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Специалисту разъяснены права и обязанности, предусмотренные ст.80 УПК РК, а именно:");
        addEmptyLine(doc);
        addJustifiedParagraph(doc, RIGHTS_ST80_SPECIALIST);
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc, "Права и обязанности специалиста, предусмотренные ст.80 УПК РК, мне разъяснены. Сущность прав ясна.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Специалист", fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Специалисту " + fio + " предложено дать показания по следующим вопросам " +
                        "(выясняются обстоятельства, связанные с заключением специалиста, " +
                        "существенными для дела вопросами, уточнением применённых методов и использованных терминов).");
        addEmptyLine(doc);

        addQABlock(doc, data, "Специалист", fio);

        addJustifiedParagraph(doc,
                "На этом допрос специалиста окончен. Специалисту разъяснено право ознакомиться с результатами допроса, " +
                        "а также право внесения в протокол замечаний, дополнений, исправлений.");
        addEmptyLine(doc);

        String familiarization = safe(data.getFamiliarization()).equals("—") ? "личного прочтения" : data.getFamiliarization();
        addJustifiedParagraph(doc, "Специалист ознакомлен с протоколом допроса путем (" + familiarization + ").");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Специалист", fio);
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc,
                "С протоколом допроса ознакомлен(а). Записано правильно. " +
                        "Заявлений, замечаний, дополнений, исправлений, подлежащих внесению в протокол не имею.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Специалист", fio);
        addEmptyLine(doc);

        addInvestigatorBlock(doc, data);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7б. ДОПОЛНИТЕЛЬНЫЙ ДОПРОС СПЕЦИАЛИСТА
    // ══════════════════════════════════════════════════════════════════════════

    private void buildDopSpecialist(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String fio = safe(data.getFio());
        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "о дополнительном допросе специалиста", 12);
        addEmptyLine(doc);
        addCityDateBlockSimple(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                currentUser.getProfession() + " по " + currentUser.getRegion() +
                        " в помещении кабинета " + safe(data.getRoom()) +
                        " административного здания, расположенного по адресу: " + safe(data.getAddrezz()) +
                        ", с соблюдением требований ст.ст.80, 197, 199, 208-212, 285 УПК РК" +
                        ", дополнительно допросил по уголовному делу №" + safe(data.getCaseNumber()) +
                        " в качестве специалиста " + fio + ".");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Перед началом следственного действия специалисту " + fio +
                        " сообщено, что он(а) будет дополнительно допрошен(а) по обстоятельствам, " +
                        "связанным с ранее данным им заключением, а также ему объявлен порядок производства допроса.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Специалисту разъяснены права и обязанности, предусмотренные ст.80 УПК РК, а именно:");
        addEmptyLine(doc);
        addJustifiedParagraph(doc, RIGHTS_ST80_SPECIALIST);
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc, "Права и обязанности специалиста, предусмотренные ст.80 УПК РК, мне разъяснены. Сущность прав ясна.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Специалист", fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Специалисту " + fio + " предложено рассказать о дополнениях или уточнениях ранее данного заключения " +
                        "и показаний по обстоятельствам, имеющим значение для дела, " +
                        "на что специалист " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);

        addQABlock(doc, data, "Специалист", fio);

        addJustifiedParagraph(doc,
                "На этом дополнительный допрос специалиста окончен. Специалисту разъяснено право ознакомиться с результатами допроса, " +
                        "а также право внесения в протокол замечаний, дополнений, исправлений.");
        addEmptyLine(doc);

        String familiarization = safe(data.getFamiliarization()).equals("—") ? "личного прочтения" : data.getFamiliarization();
        addJustifiedParagraph(doc, "Специалист ознакомлен с протоколом дополнительного допроса путем (" + familiarization + ").");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Специалист", fio);
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc,
                "С протоколом дополнительного допроса ознакомлен(а). Записано правильно. " +
                        "Заявлений, замечаний, дополнений, исправлений, подлежащих внесению в протокол не имею.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Специалист", fio);
        addEmptyLine(doc);

        addInvestigatorBlock(doc, data);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 8. ДОПРОС ПОДОЗРЕВАЕМОГО
    // ══════════════════════════════════════════════════════════════════════════

    private void buildSuspect(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String fio = safe(data.getFio());
        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "допроса подозреваемого", 12);
        addEmptyLine(doc);
        addCityDateBlock(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                currentUser.getProfession() + " по " + currentUser.getRegion() +
                        " в помещении кабинета " + safe(data.getRoom()) +
                        " административного здания, расположенного по адресу: " + safe(data.getAddrezz()) +
                        ", с соблюдением требований ст.ст.64, 80, 81, 110, 114 ч.5, 115, 197, 199, 208–210, 212, 216 УПК РК" +
                        ", с участием защитника, представившего уведомление №" + safe(data.getNotificationNumber()) +
                        " от " + safe(data.getNotificationDate()) +
                        ", допросил по уголовному делу №" + safe(data.getCaseNumber()) +
                        " в качестве подозреваемого(ой):");
        addEmptyLine(doc);
        addPersonDataTable(doc, data);
        addEmptyLine(doc);

        addInvolvedBlock(doc, data);
        addJustifiedParagraph(doc, "Перед началом следственного действия участникам объявлен порядок производства допроса.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Подозреваемому(ой) " + fio + " сообщено о предъявленном подозрении по уголовному делу №" + safe(data.getCaseNumber()) + ".");
        addEmptyLine(doc);

        // Права ст.64
        addRightsSection(doc, "suspect", fio);

        addJustifiedParagraph(doc,
                "Подозреваемый(ая) " + fio +
                        " предупрежден(а) о том, что его(её) показания могут быть использованы в качестве доказательств " +
                        "в уголовном процессе, если он(а) не воспользовался(ась) своим правом отказаться от дачи показаний " +
                        "до начала первого допроса, в том числе и при его(её) последующем отказе от этих показаний.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Подозреваемый(ая)", fio);
        addEmptyLine(doc);

        // Признание вины
        addJustifiedParagraph(doc,
                "На вопрос, признает ли подозреваемый(ая) себя виновным(ой) полностью или частично, " +
                        "либо отрицает свою вину в совершении уголовного правонарушения " + fio + " пояснил(а):   " +
                        safe(data.getConfession()));
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Подозреваемый(ая)", fio);
        addEmptyLine(doc);

        addLanguageBlock(doc, data, "Подозреваемый(ая)", fio);

        addJustifiedParagraph(doc,
                "Подозреваемому(ой) предложено рассказать об известных обстоятельствах, имеющих значение для дела, " +
                        "на что подозреваемый(ая) " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);

        addQABlock(doc, data, "Подозреваемый(ая)", fio);
        addClosingSection(doc, data, "подозреваемого", "Подозреваемый(ая)", fio);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 9. ДОПОЛНИТЕЛЬНЫЙ ДОПРОС ПОДОЗРЕВАЕМОГО
    // ══════════════════════════════════════════════════════════════════════════

    private void buildDopSuspect(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String fio = safe(data.getFio());
        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "дополнительного допроса подозреваемого", 12);
        addEmptyLine(doc);
        addCityDateBlock(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                currentUser.getProfession() + " по " + currentUser.getRegion() +
                        " в помещении кабинета " + safe(data.getRoom()) +
                        " административного здания, расположенного по адресу: " + safe(data.getAddrezz()) +
                        ", с соблюдением требований ст.ст.64, 80, 81, 110, 114 ч.5, 115, 197, 199, 208–210, 212, 216 УПК РК" +
                        ", с участием защитника, представившего уведомление №" + safe(data.getNotificationNumber()) +
                        " от " + safe(data.getNotificationDate()) +
                        ", дополнительно допросил по уголовному делу №" + safe(data.getCaseNumber()) +
                        " в качестве подозреваемого(ой):");
        addEmptyLine(doc);
        addPersonDataTable(doc, data);
        addEmptyLine(doc);

        addInvolvedBlock(doc, data);
        addJustifiedParagraph(doc, "Перед началом следственного действия участникам объявлен порядок производства дополнительного допроса.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Подозреваемому(ой) " + fio + " сообщено, что он(а) будет дополнительно допрошен(а) по уголовному делу №" + safe(data.getCaseNumber()) + ".");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Подозреваемый(ая) " + fio +
                        " предупрежден(а) о том, что его(её) показания могут быть использованы в качестве доказательств " +
                        "в уголовном процессе, если он(а) не воспользовался(ась) своим правом отказаться от дачи показаний.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Подозреваемый(ая)", fio);
        addEmptyLine(doc);

        addLanguageBlock(doc, data, "Подозреваемый(ая)", fio);

        addJustifiedParagraph(doc,
                "Подозреваемому(ой) предложено рассказать о дополнениях или уточнениях ранее данных показаний, " +
                        "на что подозреваемый(ая) " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);

        addQABlock(doc, data, "Подозреваемый(ая)", fio);
        addClosingSection(doc, data, "подозреваемого", "Подозреваемый(ая)", fio);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 10. ДОПРОС ЭКСПЕРТА
    // ══════════════════════════════════════════════════════════════════════════

    private void buildExpert(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String fio = safe(data.getFio());
        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "о допросе эксперта", 12);
        addEmptyLine(doc);
        addCityDateBlockSimple(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                currentUser.getProfession() + " по " + currentUser.getRegion() +
                        " в помещении кабинета " + safe(data.getRoom()) +
                        " административного здания, расположенного по адресу: " + safe(data.getAddrezz()) +
                        ", с соблюдением требований ст.ст.82, 197, 199, 208-212, 286 УПК РК" +
                        ", допросил по уголовному делу №" + safe(data.getCaseNumber()) +
                        " в качестве эксперта " + fio + ".");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Перед началом следственного действия эксперту " + fio +
                        " сообщено, что он(а) будет допрошен(а) по обстоятельствам, связанным с ранее данным им заключением, " +
                        "а также ему объявлен порядок производства допроса.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Эксперту разъяснены права и обязанности, предусмотренные ст.82 УПК РК, а именно:");
        addEmptyLine(doc);
        addJustifiedParagraph(doc, RIGHTS_ST82_EXPERT);
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc,
                "Права и обязанности эксперта, предусмотренные ст.82 УПК РК, мне разъяснены. Сущность прав ясна.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Эксперт", fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Эксперту " + fio + " предложено дать показания по следующим вопросам " +
                        "(выясняются обстоятельства, связанные с заключением эксперта, " +
                        "уточнением применённых методов и использованных терминов, " +
                        "существенными для дела вопросами, не требующими дополнительных исследований).");
        addEmptyLine(doc);

        addQABlock(doc, data, "Эксперт", fio);

        addJustifiedParagraph(doc,
                "На этом допрос эксперта окончен. Эксперту разъяснено право ознакомиться с результатами допроса, " +
                        "а также право внесения в протокол замечаний, дополнений, исправлений.");
        addEmptyLine(doc);

        String familiarization = safe(data.getFamiliarization()).equals("—") ? "личного прочтения" : data.getFamiliarization();
        addJustifiedParagraph(doc, "Эксперт ознакомлен с протоколом допроса путем (" + familiarization + ").");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Эксперт", fio);
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc,
                "С протоколом допроса ознакомлен(а). Записано правильно. " +
                        "Заявлений, замечаний, дополнений, исправлений, подлежащих внесению в протокол не имею.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Эксперт", fio);
        addEmptyLine(doc);

        addInvestigatorBlock(doc, data);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 10б. ДОПОЛНИТЕЛЬНЫЙ ДОПРОС ЭКСПЕРТА
    // ══════════════════════════════════════════════════════════════════════════

    private void buildDopExpert(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String fio = safe(data.getFio());
        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "о дополнительном допросе эксперта", 12);
        addEmptyLine(doc);
        addCityDateBlockSimple(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                currentUser.getProfession() + " по " + currentUser.getRegion() +
                        " в помещении кабинета " + safe(data.getRoom()) +
                        " административного здания, расположенного по адресу: " + safe(data.getAddrezz()) +
                        ", с соблюдением требований ст.ст.82, 197, 199, 208-212, 286 УПК РК" +
                        ", дополнительно допросил по уголовному делу №" + safe(data.getCaseNumber()) +
                        " в качестве эксперта " + fio + ".");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Перед началом следственного действия эксперту " + fio +
                        " сообщено, что он(а) будет дополнительно допрошен(а) по обстоятельствам, " +
                        "связанным с ранее данным им заключением, а также ему объявлен порядок производства допроса.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Эксперту разъяснены права и обязанности, предусмотренные ст.82 УПК РК, а именно:");
        addEmptyLine(doc);
        addJustifiedParagraph(doc, RIGHTS_ST82_EXPERT);
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc,
                "Права и обязанности эксперта, предусмотренные ст.82 УПК РК, мне разъяснены. Сущность прав ясна.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Эксперт", fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Эксперту " + fio + " предложено рассказать о дополнениях или уточнениях ранее данного заключения " +
                        "и показаний по обстоятельствам, имеющим значение для дела, " +
                        "на что эксперт " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);

        addQABlock(doc, data, "Эксперт", fio);

        addJustifiedParagraph(doc,
                "На этом дополнительный допрос эксперта окончен. Эксперту разъяснено право ознакомиться с результатами допроса, " +
                        "а также право внесения в протокол замечаний, дополнений, исправлений.");
        addEmptyLine(doc);

        String familiarization = safe(data.getFamiliarization()).equals("—") ? "личного прочтения" : data.getFamiliarization();
        addJustifiedParagraph(doc, "Эксперт ознакомлен с протоколом дополнительного допроса путем (" + familiarization + ").");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Эксперт", fio);
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc,
                "С протоколом дополнительного допроса ознакомлен(а). Записано правильно. " +
                        "Заявлений, замечаний, дополнений, исправлений, подлежащих внесению в протокол не имею.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Эксперт", fio);
        addEmptyLine(doc);

        addInvestigatorBlock(doc, data);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Вспомогательные блоки
    // ══════════════════════════════════════════════════════════════════════════

    /** Шапка времени: начат / окончен */
    private void addCityDateBlock(XWPFDocument doc, CaseInterrogationFullResponse data) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun r = p.createRun();
        r.setText(safe(data.getCity()) + "          " +
                (data.getDate() != null ? data.getDate().format(DATE_RU) : "«___» ________ ____ года"));
        r.setFontFamily("Times New Roman");
        r.setFontSize(12);

        addEmptyLine(doc);

        XWPFParagraph timePara = doc.createParagraph();
        timePara.setAlignment(ParagraphAlignment.RIGHT);

        List<InterrogationTimerSessionResponse> sessions = data.getTimerSessions();

        if (sessions != null && !sessions.isEmpty()) {
            InterrogationTimerSessionResponse first = sessions.get(0);
            XWPFRun r1 = timePara.createRun();
            r1.setBold(true); r1.setFontFamily("Times New Roman"); r1.setFontSize(12);
            r1.setText("Допрос начат: " + (first.getStartedAt() != null ? first.getStartedAt().format(TIME_FMT) : "__ час. __ мин."));
            r1.addBreak();

            for (int i = 0; i < sessions.size(); i++) {
                InterrogationTimerSessionResponse s = sessions.get(i);

                if (s.getPausedAt() != null) {
                    XWPFRun rp = timePara.createRun();
                    rp.setFontFamily("Times New Roman"); rp.setFontSize(12);
                    rp.setText("Прерван (согласно ст.209 УПК РК): " + s.getPausedAt().format(TIME_FMT));
                    rp.addBreak();
                }

                if (i > 0) {
                    XWPFRun rr = timePara.createRun();
                    rr.setFontFamily("Times New Roman"); rr.setFontSize(12);
                    rr.setText("Возобновлен (согласно ст.209 УПК РК): " + (s.getStartedAt() != null ? s.getStartedAt().format(TIME_FMT) : "__ час. __ мин."));
                    rr.addBreak();
                }
            }

            String end = data.getFinishedAt() != null ? data.getFinishedAt().format(TIME_FMT) : "__ час. __ мин.";
            XWPFRun re = timePara.createRun();
            re.setBold(true); re.setFontFamily("Times New Roman"); re.setFontSize(12);
            re.setText("Допрос окончен: " + end);

        } else {
            String start = data.getStartedAt() != null ? data.getStartedAt().format(TIME_FMT) : "__ час. __ мин.";
            String end   = data.getFinishedAt() != null ? data.getFinishedAt().format(TIME_FMT) : "__ час. __ мин.";
            XWPFRun rs = timePara.createRun();
            rs.setBold(true); rs.setFontFamily("Times New Roman"); rs.setFontSize(12);
            rs.setText("Допрос начат: " + start); rs.addBreak();
            XWPFRun re = timePara.createRun();
            re.setBold(true); re.setFontFamily("Times New Roman"); re.setFontSize(12);
            re.setText("Допрос окончен: " + end);
        }
    }

    private void addCityDateBlockExtended(XWPFDocument doc, CaseInterrogationFullResponse data) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun r = p.createRun();
        r.setText(safe(data.getCity()) + "          " +
                (data.getDate() != null ? data.getDate().format(DATE_RU) : "«___» ________ ____ года"));
        r.setFontFamily("Times New Roman");
        r.setFontSize(12);

        addEmptyLine(doc);

        XWPFParagraph timePara = doc.createParagraph();
        timePara.setAlignment(ParagraphAlignment.RIGHT);

        List<InterrogationTimerSessionResponse> sessions = data.getTimerSessions();

        if (sessions != null && !sessions.isEmpty()) {
            InterrogationTimerSessionResponse first = sessions.get(0);
            XWPFRun r1 = timePara.createRun();
            r1.setBold(true); r1.setFontFamily("Times New Roman"); r1.setFontSize(12);
            r1.setText("Допрос начат: " + (first.getStartedAt() != null ? first.getStartedAt().format(TIME_FMT) : "__ час. __ мин."));
            r1.addBreak();

            for (int i = 0; i < sessions.size(); i++) {
                InterrogationTimerSessionResponse s = sessions.get(i);

                if (s.getPausedAt() != null) {
                    XWPFRun rp = timePara.createRun();
                    rp.setFontFamily("Times New Roman"); rp.setFontSize(12);
                    rp.setText("Прерван (согласно ст.209 УПК РК): " + s.getPausedAt().format(TIME_FMT));
                    rp.addBreak();
                }

                if (i > 0) {
                    XWPFRun rr = timePara.createRun();
                    rr.setFontFamily("Times New Roman"); rr.setFontSize(12);
                    rr.setText("Возобновлен (согласно ст.209 УПК РК): " + (s.getStartedAt() != null ? s.getStartedAt().format(TIME_FMT) : "__ час. __ мин."));
                    rr.addBreak();
                }
            }

            String end = data.getFinishedAt() != null ? data.getFinishedAt().format(TIME_FMT) : "__ час. __ мин.";
            XWPFRun re = timePara.createRun();
            re.setBold(true); re.setFontFamily("Times New Roman"); re.setFontSize(12);
            re.setText("Допрос окончен: " + end);

        } else {
            String start = data.getStartedAt() != null ? data.getStartedAt().format(TIME_FMT) : "__ час. __ мин.";
            String end   = data.getFinishedAt() != null ? data.getFinishedAt().format(TIME_FMT) : "__ час. __ мин.";
            XWPFRun r1 = timePara.createRun();
            r1.setBold(true); r1.setFontFamily("Times New Roman"); r1.setFontSize(12);
            r1.setText("Допрос начат: " + start); r1.addBreak();

            XWPFRun r2 = timePara.createRun();
            r2.setFontFamily("Times New Roman"); r2.setFontSize(12);
            r2.setText("Прерван (согласно ст.209 УПК РК): __ час. __ мин."); r2.addBreak();

            XWPFRun r3 = timePara.createRun();
            r3.setFontFamily("Times New Roman"); r3.setFontSize(12);
            r3.setText("Возобновлен (согласно ст.209 УПК РК): __ час. __ мин."); r3.addBreak();

            XWPFRun r4 = timePara.createRun();
            r4.setBold(true); r4.setFontFamily("Times New Roman"); r4.setFontSize(12);
            r4.setText("Допрос окончен: " + end);
        }
    }

    private void addCityDateBlockSimple(XWPFDocument doc, CaseInterrogationFullResponse data) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun r = p.createRun();
        r.setText(safe(data.getCity()) + "          " +
                (data.getDate() != null ? data.getDate().format(DATE_RU) : "«___» ________ ____ года"));
        r.setFontFamily("Times New Roman");
        r.setFontSize(12);

        addEmptyLine(doc);

        XWPFParagraph timePara = doc.createParagraph();
        timePara.setAlignment(ParagraphAlignment.RIGHT);

        List<InterrogationTimerSessionResponse> sessions = data.getTimerSessions();

        if (sessions != null && !sessions.isEmpty()) {
            InterrogationTimerSessionResponse first = sessions.get(0);
            XWPFRun r1 = timePara.createRun();
            r1.setFontFamily("Times New Roman"); r1.setFontSize(12);
            r1.setText("Начало: " + (first.getStartedAt() != null ? first.getStartedAt().format(TIME_FMT) : "__ час. __ мин."));
            r1.addBreak();

            for (int i = 0; i < sessions.size(); i++) {
                InterrogationTimerSessionResponse s = sessions.get(i);

                if (s.getPausedAt() != null) {
                    XWPFRun rp = timePara.createRun();
                    rp.setFontFamily("Times New Roman"); rp.setFontSize(12);
                    rp.setText("Прерван (согласно ст.209 УПК РК): " + s.getPausedAt().format(TIME_FMT));
                    rp.addBreak();
                }

                if (i > 0) {
                    XWPFRun rr = timePara.createRun();
                    rr.setFontFamily("Times New Roman"); rr.setFontSize(12);
                    rr.setText("Возобновлен (согласно ст.209 УПК РК): " + (s.getStartedAt() != null ? s.getStartedAt().format(TIME_FMT) : "__ час. __ мин."));
                    rr.addBreak();
                }
            }

            String end = data.getFinishedAt() != null ? data.getFinishedAt().format(TIME_FMT) : "__ час. __ мин.";
            XWPFRun re = timePara.createRun();
            re.setFontFamily("Times New Roman"); re.setFontSize(12);
            re.setText("Окончание: " + end);

        } else {
            String start = data.getStartedAt() != null ? data.getStartedAt().format(TIME_FMT) : "__ час. __ мин.";
            String end   = data.getFinishedAt() != null ? data.getFinishedAt().format(TIME_FMT) : "__ час. __ мин.";
            XWPFRun tr = timePara.createRun();
            tr.setFontFamily("Times New Roman"); tr.setFontSize(12);
            tr.setText("Начало: " + start); tr.addBreak();
            XWPFRun er = timePara.createRun();
            er.setFontFamily("Times New Roman"); er.setFontSize(12);
            er.setText("Окончание: " + end);
        }
    }

    private void addInvolvedBlock(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String involved = safe(data.getInvolved()).equals("—") ? "иные лица не привлекались" : data.getInvolved();
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun r1 = p.createRun();
        r1.setText("В ходе допроса   ");
        r1.setFontFamily("Times New Roman"); r1.setFontSize(12);
        XWPFRun r2 = p.createRun();
        r2.setUnderline(UnderlinePatterns.SINGLE);
        r2.setText(involved);
        r2.setFontFamily("Times New Roman"); r2.setFontSize(12);
        addEmptyLine(doc);
    }

    private void addInvolvedSpecialistBlock(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String involved = safe(data.getInvolved()).equals("—") ? "не привлекались" : data.getInvolved();
        addJustifiedParagraph(doc,
                "В ходе дополнительного допроса привлечены в качестве специалиста(ов): " + involved +
                        ", в качестве переводчика: " + safe(data.getTranslator()) + ".");
    }

    private void addSpecialistTranslatorRights(XWPFDocument doc) {
        addJustifiedParagraph(doc, "Специалисту в соответствии со ст.ст.80, 110 УПК РК разъяснены его права и обязанности.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Специалист(ы)", "");
        addEmptyLine(doc);
        addJustifiedParagraph(doc, "Переводчику в соответствии со ст.81, 110 УПК РК разъяснены его права и обязанности.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Переводчик", "");
        addEmptyLine(doc);
    }

    private void addLanguageBlock(XWPFDocument doc, CaseInterrogationFullResponse data,
                                  String roleLabel, String fio) {
        String language = safe(data.getLanguage()).equals("—") ? "русском" : data.getLanguage();
        String translator = safe(data.getTranslator()).equals("—") ? "не нуждаюсь" : data.getTranslator();
        addJustifiedItalicParagraph(doc,
                "Я, " + fio + ", показания желаю давать на " + language + " языке, " +
                        "в помощи переводчика " + translator + ".");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);
    }

    private void addTestimonyIntroWitness(XWPFDocument doc, CaseInterrogationFullResponse data,
                                          String roleLabel, String fio) {
        addLanguageBlock(doc, data, roleLabel, fio);
        addJustifiedParagraph(doc,
                roleLabel + "у(ей) предложено рассказать об отношениях, в которых он(а) состоит с подозреваемым(ой), " +
                        "а также по существу известных ему(ей) обстоятельств, имеющих значение для дела, " +
                        "на что " + roleLabel.toLowerCase() + " " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);
    }

    private void addTestimonyIntroVictim(XWPFDocument doc, CaseInterrogationFullResponse data, String fio) {
        String language = safe(data.getLanguage()).equals("—") ? "русском" : data.getLanguage();
        addJustifiedItalicParagraph(doc,
                "После чего, потерпевший(ая) " + fio + " заявил(а):");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc,
                "Права потерпевшего, предусмотренные ст.71 УПК РК, мне разъяснены, от дачи показаний я не отказываюсь, " +
                        "желаю давать показания на " + language + " языке, которым владею свободно, " +
                        "в услугах переводчика не нуждаюсь, и это не связано с моим материальным положением, " +
                        "в мерах по обеспечению безопасности не нуждаюсь.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Потерпевший(ая)", fio);
        addEmptyLine(doc);
        addJustifiedParagraph(doc, "По существу заданных вопросов могу показать следующее:");
        addEmptyLine(doc);
    }

    private void addRightsSection(XWPFDocument doc, String role, String fio) {
        String roleLabel = getRoleLabel(role);
        addJustifiedParagraph(doc,
                roleLabel + "у(ей) " + fio + " разъяснены права и обязанности, предусмотренные " +
                        getRightsArticle(role) + " УПК РК, а именно:");
        addEmptyLine(doc);
        addJustifiedParagraph(doc, getFullRightsText(role));
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc,
                "Права и обязанности, предусмотренные " + getRightsArticle(role) +
                        " УПК РК, мне разъяснены. Сущность прав ясна.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);
    }

    private void addPersonDataTable(XWPFDocument doc, CaseInterrogationFullResponse data) {
        CaseInterrogationProtocolResponse p = data.getProtocol();
        String[][] rows = {
                {"Фамилия, имя, отчество:", safe(data.getFio())},
                {"Дата рождения:", p != null ? safe(p.getDateOfBirth()) : "—"},
                {"Место рождения:", p != null ? safe(p.getBirthPlace()) : "—"},
                {"Гражданство:", p != null ? safe(p.getCitizenship()) : "—"},
                {"Национальность:", p != null ? safe(p.getNationality()) : "—"},
                {"Образование:", p != null ? safe(p.getEducation()) : "—"},
                {"Семейное положение:", p != null ? safe(p.getMartialStatus()) : "—"},
                {"Место работы или учебы:", p != null ? safe(p.getWorkOrStudyPlace()) : "—"},
                {"Род занятий или должность:", p != null ? safe(p.getPosition()) : "—"},
                {"Место жительства и (или) регистрации:", p != null ? safe(p.getAddress()) : "—"},
                {"Контактные телефоны:", p != null ? safe(p.getContactPhone()) : "—"},
                {"Электронная почта:", p != null ? safe(p.getContactEmail()) : "—"},
                {"Иные контактные данные:", p != null ? safe(p.getOther()) : "—"},
                {"Отношение к подозреваемому, обвиняемому:", p != null ? safe(p.getRelation()) : "—"},
                {"Отношение к воинской обязанности:", p != null ? safe(p.getMilitary()) : "—"},
                {"Наличие судимости:", p != null ? safe(p.getCriminalRecord()) : "—"},
                {"Паспорт или иной документ, удостоверяющий личность:",
                        safe(data.getDocumentType()) + " " + safe(data.getNumber()) +
                                (p != null && p.getIinOrPassport() != null ? " / " + p.getIinOrPassport() : "")},
                {"Применение технических средств аудио/видео фиксации:",
                        p != null ? safe(p.getTechnical()) : "Не применяются"}
        };
        buildTable(doc, rows);
    }

    /** Таблица для дополнительного допроса потерпевшего (упрощённый состав полей) */
    private void addPersonDataTableVictimDop(XWPFDocument doc, CaseInterrogationFullResponse data) {
        CaseInterrogationProtocolResponse p = data.getProtocol();
        String[][] rows = {
                {"Фамилия, имя, отчество:", safe(data.getFio())},
                {"Дата рождения:", p != null ? safe(p.getDateOfBirth()) : "—"},
                {"Место рождения:", p != null ? safe(p.getBirthPlace()) : "—"},
                {"Национальность:", p != null ? safe(p.getNationality()) : "—"},
                {"Образование:", p != null ? safe(p.getEducation()) : "—"},
                {"Гражданство:", p != null ? safe(p.getCitizenship()) : "—"},
                {"Семейное положение:", p != null ? safe(p.getMartialStatus()) : "—"},
                {"Судимость:", p != null ? safe(p.getCriminalRecord()) : "—"},
                {"Место жительства:", p != null ? safe(p.getAddress()) : "—"},
                {"Место работы (учебы):", p != null ? safe(p.getWorkOrStudyPlace()) : "—"},
                {"Отношение к воинской обязанности:", p != null ? safe(p.getMilitary()) : "—"},
                {"Документ, удостоверяющий личность:", safe(data.getDocumentType()) + " " + safe(data.getNumber())},
                {"Телефон:", p != null ? safe(p.getContactPhone()) : "—"},
                {"Электронная почта:", p != null ? safe(p.getContactEmail()) : "—"},
                {"Иные данные о личности потерпевшего:", p != null ? safe(p.getOther()) : "—"},
                {"Иные участвующие лица:", safe(data.getInvolved())},
                {"Применение технических средств аудио/видео фиксации:", p != null ? safe(p.getTechnical()) : "Не применяются"},
                {"Отношение потерпевшего к подозреваемому:", p != null ? safe(p.getRelation()) : "—"}
        };
        buildTable(doc, rows);
    }

    private void buildTable(XWPFDocument doc, String[][] rows) {
        XWPFTable table = doc.createTable(rows.length, 2);
        removeTableBorders(table);
        for (int i = 0; i < rows.length; i++) {
            XWPFTableRow row = table.getRow(i);

            XWPFTableCell labelCell = row.getCell(0);
            setCellWidth(labelCell, 3600); setCellThinBorder(labelCell); setCellPadding(labelCell);
            XWPFRun lr = labelCell.getParagraphs().get(0).createRun();
            lr.setText(rows[i][0]); lr.setFontFamily("Times New Roman"); lr.setFontSize(11);

            XWPFTableCell valueCell = row.getCell(1);
            setCellWidth(valueCell, 5726); setCellThinBorder(valueCell); setCellPadding(valueCell);
            XWPFRun vr = valueCell.getParagraphs().get(0).createRun();
            vr.setText(rows[i][1]); vr.setFontFamily("Times New Roman"); vr.setFontSize(11);
        }
    }

    private void addQABlock(XWPFDocument doc, CaseInterrogationFullResponse data,
                            String roleLabel, String fio) {
        if (data.getQaList() == null || data.getQaList().isEmpty()) return;
        for (CaseInterrogationQAResponse qa : data.getQaList()) {
            XWPFParagraph qPara = doc.createParagraph();
            qPara.setAlignment(ParagraphAlignment.BOTH);
            qPara.setIndentationFirstLine(720);
            XWPFRun qRun = qPara.createRun();
            qRun.setBold(true);
            qRun.setText("Вопрос следователя: " + safe(qa.getQuestion()));
            qRun.setFontFamily("Times New Roman"); qRun.setFontSize(12);

            XWPFParagraph aPara = doc.createParagraph();
            aPara.setAlignment(ParagraphAlignment.BOTH);
            aPara.setIndentationFirstLine(720);
            XWPFRun aRun = aPara.createRun();
            aRun.setText("Ответ: " + (qa.getAnswer() != null && !qa.getAnswer().isBlank() ? qa.getAnswer() : "Ответ не получен."));
            aRun.setFontFamily("Times New Roman"); aRun.setFontSize(12);

            addEmptyLine(doc);
            addInlineSignatureLine(doc, roleLabel, fio);
            addEmptyLine(doc);
        }
    }

    /** Стандартное закрытие (для свидетеля, подозреваемого, потерпевшего — одиночный допрос) */
    private void addClosingSection(XWPFDocument doc, CaseInterrogationFullResponse data,
                                   String roleGenitive, String roleLabel, String fio) {
        addJustifiedParagraph(doc,
                "На этом допрос " + roleGenitive + " окончен. " +
                        "Участникам допроса разъяснено право ознакомиться с результатами допроса, " +
                        "а также право внесения в протокол заявлений, замечаний, дополнений, уточнений и исправлений.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Участникам следственного действия протокол допроса " + roleGenitive + " предъявлен для ознакомления.");
        addEmptyLine(doc);

        // Ознакомление
        XWPFParagraph famPara = doc.createParagraph();
        famPara.setAlignment(ParagraphAlignment.BOTH);
        famPara.setIndentationFirstLine(720);
        XWPFRun famLabel = famPara.createRun();
        famLabel.setText("С настоящим протоколом допроса ознакомлен(ы) путем   ");
        famLabel.setFontFamily("Times New Roman"); famLabel.setFontSize(12);
        XWPFRun famValue = famPara.createRun();
        famValue.setUnderline(UnderlinePatterns.SINGLE);
        famValue.setText(safe(data.getFamiliarization()).equals("—") ? "путем оглашения" : data.getFamiliarization());
        famValue.setFontFamily("Times New Roman"); famValue.setFontSize(12);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Протокол допроса записан правильно и отражает весь ход допроса и содержит полностью показания, данные в ходе допроса.");
        addEmptyLine(doc);

        XWPFParagraph appPara = doc.createParagraph();
        appPara.setAlignment(ParagraphAlignment.BOTH);
        appPara.setIndentationFirstLine(720);
        XWPFRun appLabel = appPara.createRun();
        appLabel.setText("Заявлений, замечаний, дополнений, уточнений и исправлений, подлежащих внесению в протокол   ");
        appLabel.setFontFamily("Times New Roman"); appLabel.setFontSize(12);
        XWPFRun appValue = appPara.createRun();
        appValue.setUnderline(UnderlinePatterns.SINGLE);
        appValue.setText(safe(data.getApplication()).equals("—") ? "не имею" : data.getApplication());
        appValue.setFontFamily("Times New Roman"); appValue.setFontSize(12);
        addEmptyLine(doc);

        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addInvestigatorBlock(doc, data);
    }

    /** Закрытие для доп. допроса с несколькими участниками (свидетель + специалист + переводчик) */
    private void addClosingSectionMultiParty(XWPFDocument doc, CaseInterrogationFullResponse data,
                                             String protocolName, String roleLabel, String fio) {
        addJustifiedParagraph(doc,
                "На этом " + protocolName + " окончен. " +
                        "Участникам допроса разъяснено право ознакомиться с результатами допроса, " +
                        "а также право внесения в протокол заявлений, замечаний, дополнений, уточнений и исправлений.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addInlineSignatureLine(doc, "Специалист(ы)", "");
        addInlineSignatureLine(doc, "Переводчик", "");
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Участникам следственного действия протокол " + protocolName + " предъявлен для ознакомления.");
        addEmptyLine(doc);

        String familiarization = safe(data.getFamiliarization()).equals("—")
                ? "личного прочтения, оглашения по просьбе участника" : data.getFamiliarization();
        addJustifiedParagraph(doc,
                "С настоящим протоколом допроса ознакомлен(ы) путем " + familiarization + ", " +
                        "а также с фонограммой и видеограммой хода следственного действия путем воспроизведения.");
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc,
                "Протокол " + protocolName + " записан правильно, и его фонограмма и видеограмма отражает " +
                        "весь ход допроса и содержит полностью показания, данные в ходе допроса.");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc,
                "Заявлений, замечаний, дополнений, уточнений и исправлений, подлежащих внесению в протокол, не имею(ем).");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addInlineSignatureLine(doc, "Специалист(ы)", "");
        addInlineSignatureLine(doc, "Переводчик", "");
        addEmptyLine(doc);

        addInvestigatorBlock(doc, data);
    }

    private void addInvestigatorBlock(XWPFDocument doc, CaseInterrogationFullResponse data) {
        XWPFParagraph label = doc.createParagraph();
        XWPFRun lr = label.createRun();
        lr.setBold(true); lr.setText("Допросил, протокол составил:");
        lr.setFontFamily("Times New Roman"); lr.setFontSize(12);
        addEmptyLine(doc);

        XWPFParagraph profPara = doc.createParagraph();
        profPara.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun pr = profPara.createRun();
        pr.setBold(true); pr.setText(safe(data.getInvestigatorProfession()));
        pr.setFontFamily("Times New Roman"); pr.setFontSize(12);
        addEmptyLine(doc);

        XWPFParagraph sigPara = doc.createParagraph();
        XWPFRun sr = sigPara.createRun();
        sr.setText("___________________________                    " + safe(data.getInvestigator()));
        sr.setFontFamily("Times New Roman"); sr.setFontSize(12);

        XWPFParagraph notePara = doc.createParagraph();
        notePara.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun nr = notePara.createRun();
        nr.setText("(подпись)"); nr.setFontFamily("Times New Roman"); nr.setFontSize(10); nr.setColor("888888");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Тексты прав
    // ══════════════════════════════════════════════════════════════════════════

    private String getFullRightsText(String role) {
        return switch (role.toLowerCase()) {
            case "victim"   -> RIGHTS_ST71;
            case "suspect"  -> RIGHTS_ST64;
            case "witness"  -> RIGHTS_ST78;
            default -> throw new IllegalStateException("Unexpected role: " + role);
        };
    }

    private String getRoleLabel(String role) {
        return switch (role.toLowerCase()) {
            case "victim"            -> "Потерпевший(ая)";
            case "suspect"           -> "Подозреваемый(ая)";
            case "witness"           -> "Свидетель";
            case "witness_protected" -> "Свидетель, имеющий право на защиту";
            case "specialist"        -> "Специалист";
            case "expert"            -> "Эксперт";
            default -> "Свидетель";
        };
    }

    private String getRightsArticle(String role) {
        return switch (role.toLowerCase()) {
            case "victim"  -> "ст.71";
            case "suspect" -> "ст.64";
            case "witness" -> "ст.78";
            default        -> "ст.78";
        };
    }

    // ─── Тексты статей УПК РК ─────────────────────────────────────────────

    private static final String RIGHTS_ST71 =
            "Потерпевший имеет право:\n" +
                    "1) знать о предъявленном подозрении и обвинении; 2) давать показания на родном языке или языке, которым владеет; " +
                    "3) представлять доказательства; 4) заявлять ходатайства и отводы; 5) пользоваться бесплатной помощью переводчика; " +
                    "6) иметь представителя; 7) получать имущество, изъятое у него органом уголовного преследования в качестве средства доказывания; " +
                    "8) примириться, в том числе в порядке медиации, с подозреваемым, обвиняемым в случаях, предусмотренных законом; " +
                    "8-1) выразить согласие на применение приказного производства; " +
                    "9) знакомиться с протоколами следственных действий, производимых с его участием, и подавать на них замечания; " +
                    "10) участвовать с разрешения следователя или дознавателя в следственных действиях; " +
                    "11) знакомиться по окончании досудебного расследования со всеми материалами дела; " +
                    "12) заявлять ходатайства о предоставлении мер безопасности ему и членам его семьи; " +
                    "13) получить копии постановлений о признании его потерпевшим или отказе в этом; " +
                    "14) участвовать в судебном разбирательстве дела; 15) выступать в судебных прениях; " +
                    "16) поддерживать обвинение; 17) знакомиться с протоколом судебного заседания и подавать замечания; " +
                    "18) приносить жалобы на действия (бездействие) органа, ведущего уголовный процесс; " +
                    "19) обжаловать приговор и постановления суда; " +
                    "20) знать о принесенных по делу жалобах и протестах; " +
                    "21) защищать свои права и законные интересы иными способами, не противоречащими закону; " +
                    "22) знать о намерении сторон заключить процессуальное соглашение; " +
                    "23) на получение компенсации в соответствии с законодательством РК о Фонде компенсации потерпевшим.\n\n" +
                    "Потерпевший обязан: явиться по вызову органа, ведущего уголовный процесс, правдиво сообщить все известные " +
                    "по делу обстоятельства и ответить на поставленные вопросы; не разглашать сведения об обстоятельствах, " +
                    "известных ему по делу; соблюдать установленный порядок при производстве следственных действий и во время судебного заседания.\n\n" +
                    "При неявке потерпевшего по вызову без уважительных причин он может быть подвергнут принудительному приводу " +
                    "в порядке, предусмотренном статьей 157 УПК, и на него может быть наложено денежное взыскание " +
                    "в порядке, предусмотренном статьей 160 УПК.\n\n" +
                    "За отказ от дачи показаний и дачу заведомо ложных показаний потерпевший несет уголовную ответственность.\n\n" +
                    "Потерпевшему(ей) разъяснено право отказаться от дачи показаний, уличающих в совершении уголовного правонарушения " +
                    "его самого, супруга (супруги), близких родственников.";

    /** Полный текст ст.71 для дополнительного допроса потерпевшего */
    private static final String RIGHTS_ST71_FULL =
            "1. Потерпевшим в уголовном процессе признается лицо, в отношении которого есть основание полагать, что ему " +
                    "непосредственно уголовным правонарушением причинен моральный, физический или имущественный вред.\n\n" +
                    "6. Потерпевший имеет право: 1) знать о предъявленном подозрении и обвинении; " +
                    "2) давать показания на родном языке или языке, которым владеет; 3) представлять доказательства; " +
                    "4) заявлять ходатайства и отводы; 5) пользоваться бесплатной помощью переводчика; 6) иметь представителя; " +
                    "7) получать имущество, изъятое у него органом уголовного преследования в качестве средства доказывания; " +
                    "8) примириться, в том числе в порядке медиации, с подозреваемым, обвиняемым в случаях, предусмотренных законом; " +
                    "8-1) выразить согласие на применение приказного производства; " +
                    "9) знакомиться с протоколами следственных действий, снимать и получать копии протоколов; " +
                    "10) участвовать с разрешения следователя в следственных действиях; " +
                    "11) знакомиться по окончании расследования с материалами дела; " +
                    "12) заявлять ходатайства о предоставлении мер безопасности; " +
                    "13) получить копии постановлений; 14) участвовать в судебном разбирательстве; " +
                    "15) выступать в судебных прениях; 16) поддерживать обвинение; " +
                    "17) знакомиться с протоколом судебного заседания; " +
                    "18) приносить жалобы на действия органа, ведущего уголовный процесс; " +
                    "19) обжаловать приговор и постановления суда; 20) знать о принесенных по делу жалобах и протестах; " +
                    "21) защищать свои права и законные интересы иными способами; " +
                    "22) знать о намерении сторон заключить процессуальное соглашение; " +
                    "23) на получение компенсации в соответствии с законодательством РК о Фонде компенсации потерпевшим.\n\n" +
                    "8. Потерпевший обязан: явиться по вызову органа, ведущего уголовный процесс, правдиво сообщить все известные " +
                    "по делу обстоятельства и ответить на поставленные вопросы; не разглашать сведения об обстоятельствах, " +
                    "известных ему по делу; соблюдать установленный порядок при производстве следственных действий и во время судебного заседания.\n\n" +
                    "За отказ от дачи показаний и дачу заведомо ложных показаний потерпевший несет уголовную ответственность.";

    private static final String RIGHTS_ST78 =
            "Свидетель имеет право:\n" +
                    "1) отказаться от дачи показаний, которые могут повлечь для него самого, его супруга (супруги) или близких " +
                    "родственников преследование за совершение уголовно наказуемого деяния или административного правонарушения; " +
                    "2) давать показания на своем родном языке или языке, которым владеет; " +
                    "3) пользоваться бесплатной помощью переводчика; 4) заявлять отвод переводчику; " +
                    "5) собственноручной записи показаний в протоколе допроса; " +
                    "6) приносить жалобы на действия дознавателя, следователя, прокурора и суда, заявлять ходатайства, " +
                    "в том числе о принятии мер безопасности.\n\n" +
                    "Свидетель имеет право давать показания в присутствии своего адвоката.\n\n" +
                    "Свидетелю обеспечивается возмещение расходов, понесенных им при производстве по уголовному делу.\n\n" +
                    "Свидетель обязан:\n" +
                    "1) явиться по вызову дознавателя, следователя, прокурора и суда; " +
                    "2) правдиво сообщить все известное по делу и ответить на поставленные вопросы; " +
                    "3) не разглашать сведения об обстоятельствах, известных ему по делу, если он был предупрежден об этом; " +
                    "4) соблюдать установленный порядок при производстве следственных действий и во время судебного заседания.\n\n" +
                    "За дачу ложных показаний, отказ от дачи показаний свидетель несет уголовную ответственность, " +
                    "предусмотренную ст.ст.420, 421 УК РК.\n\n" +
                    "Свидетелю разъяснено право отказаться от дачи показаний, уличающих в совершении уголовного правонарушения " +
                    "его самого, супруга (супруги), близких родственников.";

    private static final String RIGHTS_ST64 =
            "Подозреваемый имеет право:\n" +
                    "1) получить разъяснение принадлежащих прав; 2) знать, в чем подозревается; " +
                    "3) пригласить защитника самостоятельно или через родственников; " +
                    "4) пользоваться правами гражданского ответчика в случае признания таковым; " +
                    "5) иметь свидание с защитником наедине и конфиденциально, в том числе до начала допроса; " +
                    "6) давать показания только в присутствии защитника; " +
                    "7) получить копии постановлений о признании подозреваемым; " +
                    "8) отказаться от дачи показаний; " +
                    "9) получить разъяснение о порядке и условиях применения меры пресечения; " +
                    "10) представлять доказательства; 11) заявлять ходатайства и отводы; " +
                    "12) давать показания на родном языке или языке, которым владеет; " +
                    "13) пользоваться бесплатной помощью переводчика; " +
                    "14) участвовать с разрешения органа уголовного преследования в следственных действиях; " +
                    "15) примириться с потерпевшим в случаях, предусмотренных законом; " +
                    "16) заявить ходатайство прокурору о заключении процессуального соглашения; " +
                    "16-1) заявить ходатайство о применении приказного производства; " +
                    "17) знакомиться с протоколами следственных действий и подавать замечания; " +
                    "18) приносить жалобы на действия (бездействие) следователя, дознавателя, прокурора и суда; " +
                    "19) защищать свои права и законные интересы иными способами, не противоречащими закону; " +
                    "20) при назначении и производстве экспертизы осуществлять действия, предусмотренные ст.274, 286 УПК РК; " +
                    "21) знакомиться по окончании расследования с материалами дела; " +
                    "22) возражать против прекращения уголовного преследования; " +
                    "23) безотлагательно уведомляться о принятии процессуальных решений; " +
                    "24) ходатайствовать о дополнительном допросе показывающего против него свидетеля.\n\n" +
                    "Подозреваемый имеет право отказаться от дачи показаний до начала первого допроса.";

    private static final String RIGHTS_ST65_1 =
            "Свидетель, имеющий право на защиту, имеет право:\n" +
                    "1) получить разъяснение принадлежащих прав; 2) получить разъяснение о статусе свидетеля, имеющего право на защиту; " +
                    "3) ознакомиться с постановлением о назначении экспертизы; " +
                    "4) ознакомиться с заключением экспертизы; 5) отказаться от дачи показаний; " +
                    "6) пригласить защитника самостоятельно или через родственников; " +
                    "7) давать показания в присутствии защитника; " +
                    "8) давать показания на родном языке или языке, которым владеет; " +
                    "9) пользоваться бесплатной помощью переводчика; " +
                    "10) собственноручной записи своих показаний в протоколе допроса; " +
                    "11) знакомиться с документами, за исключением материалов оперативно-розыскных мероприятий; " +
                    "12) знакомиться с протоколами следственных действий и подавать на них замечания; " +
                    "13) заявлять ходатайства, касающиеся его прав и законных интересов; " +
                    "14) заявлять отводы; 15) на очную ставку с теми, кто свидетельствует против него; " +
                    "16) приносить жалобы на действия (бездействие) дознавателя, следователя, прокурора.\n\n" +
                    "Свидетель, имеющий право на защиту, обязан: являться по вызовам суда, прокурора, " +
                    "лица, осуществляющего досудебное расследование; соблюдать установленный порядок при " +
                    "производстве следственных действий и во время судебного заседания.\n\n" +
                    "Права и обязанности разъясняются перед началом первого процессуального действия с его участием.\n\n" +
                    "Если свидетель, имеющий право на защиту, не воспользовался своим правом отказаться от дачи показаний " +
                    "до начала первого допроса, он должен быть предупрежден о том, что его показания могут быть использованы " +
                    "в качестве доказательств в уголовном процессе.\n\n" +
                    "За неявку без уважительных причин по вызову органа, ведущего уголовный процесс, на свидетеля, " +
                    "имеющего право на защиту, может быть наложено денежное взыскание.";

    private static final String RIGHTS_ST80_SPECIALIST =
            "Специалист имеет право:\n" +
                    "1) знакомиться с материалами, относящимися к предмету исследования; " +
                    "2) заявлять ходатайства о предоставлении дополнительных материалов; " +
                    "3) знать цель своего вызова; " +
                    "4) отказаться от участия в производстве по делу, если не обладает соответствующими специальными знаниями; " +
                    "5) с разрешения органа, ведущего уголовный процесс, задавать вопросы участникам следственного действия; " +
                    "6) проводить исследование материалов дела с отражением хода и результатов в протоколе; " +
                    "7) знакомиться с протоколом следственного действия и делать замечания; " +
                    "8) приносить жалобы на действия органа, ведущего уголовный процесс; " +
                    "9) пользоваться бесплатной помощью переводчика; 10) заявлять отвод переводчику; " +
                    "11) заявлять ходатайство о принятии мер безопасности; " +
                    "12) получать возмещение расходов и вознаграждение за выполненную работу.\n\n" +
                    "Специалист не вправе:\n" +
                    "1) вести переговоры с участниками процесса без ведома органа, ведущего уголовный процесс; " +
                    "2) самостоятельно собирать материалы исследования.\n\n" +
                    "Специалист обязан:\n" +
                    "1) явиться по вызову органа, ведущего уголовный процесс; " +
                    "2) участвовать в производстве следственных действий, используя специальные знания; " +
                    "3) давать пояснения по поводу выполняемых действий; " +
                    "4) не разглашать сведения об обстоятельствах дела; " +
                    "5) соблюдать порядок при производстве следственных действий; " +
                    "6) обеспечить сохранность представленных на исследование объектов.\n\n" +
                    "За отказ или уклонение от выполнения обязанностей без уважительных причин на специалиста " +
                    "может быть наложено денежное взыскание (ст.160 УПК). " +
                    "В случае заведомо ложного заключения специалист несет уголовную ответственность.";

    private static final String RIGHTS_ST82_EXPERT =
            "Эксперт имеет право:\n" +
                    "1) знакомиться с материалами дела, относящимися к предмету экспертизы; " +
                    "2) заявлять ходатайства о предоставлении ему дополнительных материалов, необходимых для дачи заключения; " +
                    "3) с разрешения органа, ведущего уголовный процесс, участвовать в производстве следственных и судебных действий, " +
                    "задавать вопросы участникам следственного действия; " +
                    "4) знакомиться с протоколом следственного или судебного действия, в котором он участвовал, " +
                    "а также в соответствующей части с протоколом судебного заседания и делать замечания; " +
                    "5) указывать в заключении на обстоятельства, имеющие значение для дела, по поводу которых ему не были поставлены вопросы; " +
                    "6) приносить жалобы на действия органа, ведущего уголовный процесс; " +
                    "7) пользоваться бесплатной помощью переводчика; " +
                    "8) заявлять отвод переводчику; " +
                    "9) заявлять ходатайство о принятии мер безопасности; " +
                    "10) получать возмещение расходов и вознаграждение за выполненную работу, если участие в производстве " +
                    "по делу не входит в круг его должностных обязанностей.\n\n" +
                    "Эксперт не вправе:\n" +
                    "1) вступать в контакт с участниками процесса по вопросам, связанным с проведением экспертизы, " +
                    "без ведома органа, ведущего уголовный процесс; " +
                    "2) самостоятельно собирать материалы для экспертного исследования; " +
                    "3) проводить исследования, которые могут повлечь полное уничтожение объектов либо изменение их внешнего вида " +
                    "или основных свойств, без разрешения органа, ведущего уголовный процесс.\n\n" +
                    "Эксперт обязан:\n" +
                    "1) явиться по вызову органа, ведущего уголовный процесс; " +
                    "2) провести полное исследование и дать обоснованное и объективное письменное заключение по поставленным вопросам; " +
                    "3) отказаться от дачи заключения и сообщить об этом органу, ведущему уголовный процесс, " +
                    "если поставленные вопросы выходят за пределы его специальных знаний или если предоставленные материалы недостаточны; " +
                    "4) не разглашать сведения об обстоятельствах дела и иные сведения, " +
                    "ставшие ему известными в связи с проведением экспертизы; " +
                    "5) обеспечить сохранность представленных на экспертизу объектов; " +
                    "6) соблюдать установленный порядок при производстве следственных действий и во время судебного заседания.\n\n" +
                    "За дачу заведомо ложного заключения эксперт несет уголовную ответственность, установленную законом.\n\n" +
                    "За отказ или уклонение от выполнения своих обязанностей без уважительных причин на эксперта " +
                    "может быть наложено денежное взыскание в порядке, установленном статьей 160 УПК РК.";

    // ══════════════════════════════════════════════════════════════════════════
    // Вспомогательные POI-методы (без изменений)
    // ══════════════════════════════════════════════════════════════════════════

    private void addCenteredBoldParagraph(XWPFDocument doc, String text, int fontSize) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setBold(true); r.setText(text); r.setFontFamily("Times New Roman"); r.setFontSize(fontSize);
    }

    private void addCenteredParagraph(XWPFDocument doc, String text, int fontSize) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setText(text); r.setFontFamily("Times New Roman"); r.setFontSize(fontSize);
    }

    private void addJustifiedParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        p.setIndentationFirstLine(720);
        XWPFRun r = p.createRun();
        r.setText(text); r.setFontFamily("Times New Roman"); r.setFontSize(12);
    }

    private void addJustifiedItalicParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        p.setIndentationFirstLine(720);
        XWPFRun r = p.createRun();
        r.setItalic(true); r.setText(text); r.setFontFamily("Times New Roman"); r.setFontSize(12);
    }

    private void addInlineSignatureLine(XWPFDocument doc, String roleLabel, String fio) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r1 = p.createRun();
        r1.setText(roleLabel + ":   "); r1.setFontFamily("Times New Roman"); r1.setFontSize(12);
        XWPFRun r2 = p.createRun();
        String fioLine = (fio != null && !fio.isBlank()) ? fio : "";
        r2.setText("____________________          " + fioLine); r2.setFontFamily("Times New Roman"); r2.setFontSize(12);
    }

    private void addEmptyLine(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setFontSize(6); r.setText("");
    }

    private void setPageMargins(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().addNewSectPr();
        CTPageMar pageMar = sectPr.addNewPgMar();
        pageMar.setTop(BigInteger.valueOf(1440));
        pageMar.setBottom(BigInteger.valueOf(1440));
        pageMar.setLeft(BigInteger.valueOf(1800));
        pageMar.setRight(BigInteger.valueOf(1080));
    }

    private void setCellWidth(XWPFTableCell cell, int widthDxa) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTblWidth w = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
        w.setW(BigInteger.valueOf(widthDxa)); w.setType(STTblWidth.DXA);
    }

    private void setCellThinBorder(XWPFTableCell cell) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcBorders b = tcPr.isSetTcBorders() ? tcPr.getTcBorders() : tcPr.addNewTcBorders();
        for (CTBorder border : new CTBorder[]{b.addNewTop(), b.addNewBottom(), b.addNewLeft(), b.addNewRight()}) {
            border.setVal(STBorder.SINGLE); border.setSz(BigInteger.valueOf(2)); border.setColor("000000");
        }
    }

    private void setCellPadding(XWPFTableCell cell) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcMar mar = tcPr.isSetTcMar() ? tcPr.getTcMar() : tcPr.addNewTcMar();
        for (CTTblWidth w : new CTTblWidth[]{
                mar.isSetTop() ? mar.getTop() : mar.addNewTop(),
                mar.isSetBottom() ? mar.getBottom() : mar.addNewBottom(),
                mar.isSetLeft() ? mar.getLeft() : mar.addNewLeft(),
                mar.isSetRight() ? mar.getRight() : mar.addNewRight()}) {
            w.setW(BigInteger.valueOf(80)); w.setType(STTblWidth.DXA);
        }
    }

    private void removeTableBorders(XWPFTable table) {
        CTTbl ctTbl = table.getCTTbl();
        CTTblPr tblPr = ctTbl.getTblPr() != null ? ctTbl.getTblPr() : ctTbl.addNewTblPr();
        CTTblBorders borders = tblPr.getTblBorders() != null ? tblPr.getTblBorders() : tblPr.addNewTblBorders();
        for (CTBorder b : new CTBorder[]{
                borders.isSetTop() ? borders.getTop() : borders.addNewTop(),
                borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom(),
                borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft(),
                borders.isSetRight() ? borders.getRight() : borders.addNewRight(),
                borders.isSetInsideH() ? borders.getInsideH() : borders.addNewInsideH(),
                borders.isSetInsideV() ? borders.getInsideV() : borders.addNewInsideV()}) {
            b.setVal(STBorder.NONE);
        }
    }

    private String safe(String val) {
        return (val != null && !val.isBlank()) ? val : "—";
    }
}