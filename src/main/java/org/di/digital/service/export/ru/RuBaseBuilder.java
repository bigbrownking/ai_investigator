package org.di.digital.service.export.ru;

import org.apache.poi.xwpf.usermodel.*;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.dto.response.CaseInterrogationProtocolResponse;
import org.di.digital.model.User;
import org.di.digital.service.export.BaseInterrogationDocBuilder;
import org.di.digital.util.LocalizationHelper;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabStop;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Базовый класс для всех русскоязычных протоколов.
 * Содержит:
 *  - форматирование даты/времени на русском
 *  - общие блоки (шапка, участники, закрытие, следователь)
 *  - полные тексты статей УПК РК на русском
 */
public abstract class RuBaseBuilder extends BaseInterrogationDocBuilder {

    private static final DateTimeFormatter DATE_RU =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'года'", new Locale("ru"));
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH 'час.' mm 'мин.'");

    protected final LocalizationHelper localizationHelper;

    protected RuBaseBuilder(LocalizationHelper localizationHelper) {
        this.localizationHelper = localizationHelper;
    }

    // ── Форматирование времени ────────────────────────────────────────────────

    @Override
    protected String formatTime(LocalDateTime dt) {
        return dt.format(TIME_FMT);
    }

    @Override
    protected String getSignatureNote() {
        return "(подпись)";
    }

    // ── Шапка (город слева, дата справа) + время ─────────────────────────────

    /** Стандартная шапка: город / дата + время с перерывами, выровнено вправо */
    protected void addCityDateBlock(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String dateStr = data.getDate() != null ? data.getDate().format(DATE_RU) : "«___» ________ ____ года";
        addHeaderRow(doc, safe(data.getCity()), dateStr);
        addEmptyLine(doc);

        boolean multiDay = hasMultipleDays(data.getTimerSessions(), data.getFinishedAt());
        addTimeBlockLocalized(doc, data, multiDay);
        addEmptyLine(doc);
    }

    private void addTimeBlockLocalized(XWPFDocument doc, CaseInterrogationFullResponse data, boolean multiDay) {
        XWPFParagraph timePara = doc.createParagraph();
        timePara.setAlignment(ParagraphAlignment.RIGHT);
        var sessions = data.getTimerSessions();
        boolean hasRealPause = sessions != null && sessions.size() > 1;

        if (sessions != null && !sessions.isEmpty()) {
            var first = sessions.get(0);
            String startTime = first.getStartedAt() != null
                    ? formatTimeWithDateIfNeeded(first.getStartedAt(), multiDay) : "__ час. __ мин.";
            addTimeRun(timePara, "Допрос начат: " + startTime, true, true);

            String pausedTime = (hasRealPause && sessions.get(0).getPausedAt() != null)
                    ? formatTimeWithDateIfNeeded(sessions.get(0).getPausedAt(), multiDay) : "- час. - мин.";
            addTimeRun(timePara, "Прерван (согласно ст.209 УПК РК): " + pausedTime, true, true);

            String resumedTime = (hasRealPause && sessions.get(1).getStartedAt() != null)
                    ? formatTimeWithDateIfNeeded(sessions.get(1).getStartedAt(), multiDay) : "- час. - мин.";
            addTimeRun(timePara, "Возобновлен (согласно ст.209 УПК РК): " + resumedTime, true, true);

            String endTime = formatTimeWithDateIfNeeded(data.getFinishedAt(), multiDay);
            addTimeRun(timePara, "Допрос окончен: " + endTime, true, false);
        } else {
            addTimeRun(timePara, "Допрос начат: __ час. __ мин.", true, true);
            addTimeRun(timePara, "Прерван (согласно ст.209 УПК РК): - час. - мин.", true, true);
            addTimeRun(timePara, "Возобновлен (согласно ст.209 УПК РК): - час. - мин.", true, true);
            addTimeRun(timePara, "Допрос окончен: __ час. __ мин.", true, false);
        }
    }
    protected String formatTimeWithDateIfNeeded(LocalDateTime dt, boolean showDate) {
        if (dt == null) return "__ час. __ мин.";
        if (showDate) {
            return dt.format(DateTimeFormatter.ofPattern("d MMMM yyyy 'года' HH 'час.' mm 'мин.'", new Locale("ru")));
        }
        return formatTime(dt);
    }
    /** Упрощённая шапка для специалиста/эксперта: Начало / Окончание */
    protected void addCityDateBlockSimple(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String dateStr = data.getDate() != null ? data.getDate().format(DATE_RU) : "«___» ________ ____ года";
        addHeaderRow(doc, safe(data.getCity()), dateStr);
        addEmptyLine(doc);
        addTimeBlockSimple(doc,
                data.getTimerSessions(), data.getFinishedAt(),
                ParagraphAlignment.RIGHT,
                "Начало", "Окончание",
                "__ час. __ мин.");
    }

    // ── Вводный текст ─────────────────────────────────────────────────────────

    protected String buildIntroLine(User currentUser, CaseInterrogationFullResponse data) {
        return localizationHelper.getGenitive(currentUser.getProfession().getRuName())
                +" "+ localizationHelper.getGenitive(currentUser.getAdministration().getRuName())
                + " "+localizationHelper.getGenitive(currentUser.getRegion().getRuName())
                + " в помещении кабинета " + safe(data.getRoom())
                + " административного здания, расположенного по адресу: " + safe(data.getAddrezz());
    }

    // ── Участники допроса ─────────────────────────────────────────────────────

    protected void addInvolvedBlock(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String involved = safe(data.getInvolved()).equals("—") ? "иные лица не привлекались" : data.getInvolved();
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun r1 = p.createRun();
        r1.setText("В ходе допроса   ");
        r1.setFontFamily(FONT);
        r1.setFontSize(FONT_SIZE);
        XWPFRun r2 = p.createRun();
        r2.setUnderline(UnderlinePatterns.SINGLE);
        r2.setText(involved);
        r2.setFontFamily(FONT);
        r2.setFontSize(FONT_SIZE);
        XWPFRun r3 = p.createRun();
        r3.setText(".");
        r3.setFontFamily(FONT);
        r3.setFontSize(FONT_SIZE);
        addEmptyLine(doc);
    }

    protected void addInvolvedSpecialistBlock(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String involved = safe(data.getInvolved()).equals("—") ? "не привлекались" : data.getInvolved();
        addJustifiedParagraph(doc,
                "В ходе дополнительного допроса привлечены в качестве специалиста(ов): " + involved
                        + ", в качестве переводчика: " + safe(data.getTranslator()) + ".");
    }

    protected void addSpecialistTranslatorRights(XWPFDocument doc) {
        addJustifiedParagraph(doc, "Специалисту в соответствии со ст.ст.80, 110 УПК РК разъяснены его права и обязанности.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Специалист(ы)", "");
        addEmptyLine(doc);
        addJustifiedParagraph(doc, "Переводчику в соответствии со ст.81, 110 УПК РК разъяснены его права и обязанности.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Переводчик", "");
        addEmptyLine(doc);
    }

    // ── Язык показаний ───────────────────────────────────────────────────────

    protected void addLanguageBlock(XWPFDocument doc, CaseInterrogationFullResponse data,
                                    String roleLabel, String fio) {
        String language = safe(data.getLanguage()).equals("—") ? "русском" : data.getLanguage();
        String translator = safe(data.getTranslator()).equals("—") ? "не нуждаюсь" : data.getTranslator();
        addJustifiedItalicParagraph(doc,
                "Я, " + fio + ", показания желаю давать на " + language + " языке, "
                        + "в помощи переводчика " + translator + ".");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);
    }

    // ── Права по статьям ─────────────────────────────────────────────────────

    protected void addRightsSection(XWPFDocument doc, String roleLabel, String fio,
                                    String article, String rightsText) {
        addJustifiedParagraph(doc,
                roleLabel + " " + fio + " разъяснены права и обязанности, предусмотренные "
                        + article + " УПК РК, а именно:");
        addEmptyLine(doc);
        addJustifiedParagraph(doc, rightsText);
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc,
                "Права и обязанности, предусмотренные " + article
                        + " УПК РК, мне разъяснены. Сущность прав ясна.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);
    }

    // ── Закрывающие блоки ────────────────────────────────────────────────────

    protected void addClosingSection(XWPFDocument doc, CaseInterrogationFullResponse data,
                                     String roleGenitive, String roleLabel, String fio) {
        addJustifiedParagraph(doc,
                "На этом допрос " + roleGenitive + " окончен. "
                        + "Участникам допроса разъяснено право ознакомиться с результатами допроса, "
                        + "а также право внесения в протокол заявлений, замечаний, дополнений, уточнений и исправлений.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Участникам следственного действия протокол допроса " + roleGenitive + " предъявлен для ознакомления.");
        addEmptyLine(doc);

        XWPFParagraph famPara = doc.createParagraph();
        famPara.setAlignment(ParagraphAlignment.BOTH);
        famPara.setIndentationFirstLine(720);
        XWPFRun famLabel = famPara.createRun();
        famLabel.setText("С настоящим протоколом допроса ознакомлен(ы) путем   ");
        famLabel.setFontFamily(FONT);
        famLabel.setFontSize(FONT_SIZE);
        XWPFRun famValue = famPara.createRun();
        famValue.setUnderline(UnderlinePatterns.SINGLE);
        famValue.setText(safe(data.getFamiliarization()).equals("—") ? "путем оглашения" : data.getFamiliarization());
        famValue.setFontFamily(FONT);
        famValue.setFontSize(FONT_SIZE);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Протокол допроса записан правильно и отражает весь ход допроса и содержит полностью показания, данные в ходе допроса.");
        addEmptyLine(doc);

        XWPFParagraph appPara = doc.createParagraph();
        appPara.setAlignment(ParagraphAlignment.BOTH);
        appPara.setIndentationFirstLine(720);
        XWPFRun appLabel = appPara.createRun();
        appLabel.setText("Заявлений, замечаний, дополнений, уточнений и исправлений, подлежащих внесению в протокол   ");
        appLabel.setFontFamily(FONT);
        appLabel.setFontSize(FONT_SIZE);
        XWPFRun appValue = appPara.createRun();
        appValue.setUnderline(UnderlinePatterns.SINGLE);
        appValue.setText(safe(data.getApplication()).equals("—") ? "не имею" : data.getApplication());
        appValue.setFontFamily(FONT);
        appValue.setFontSize(FONT_SIZE);
        addEmptyLine(doc);

        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addInvestigatorBlock(doc, "Допросил, протокол составил:",
                data.getInvestigatorProfession(), formatFio(data.getInvestigator()));
    }

    protected void addClosingSectionMultiParty(XWPFDocument doc, CaseInterrogationFullResponse data,
                                               String protocolName, String roleLabel, String fio) {
        addJustifiedParagraph(doc,
                "На этом " + protocolName + " окончен. "
                        + "Участникам допроса разъяснено право ознакомиться с результатами допроса, "
                        + "а также право внесения в протокол заявлений, замечаний, дополнений, уточнений и исправлений.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addInlineSignatureLine(doc, "Специалист(ы)", "");
        addInlineSignatureLine(doc, "Переводчик", "");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Участникам следственного действия протокол " + protocolName + " предъявлен для ознакомления.");
        addEmptyLine(doc);

        String fam = safe(data.getFamiliarization()).equals("—")
                ? "личного прочтения, оглашения по просьбе участника" : data.getFamiliarization();
        addJustifiedParagraph(doc,
                "С настоящим протоколом допроса ознакомлен(ы) путем " + fam
                        + ", а также с фонограммой и видеограммой хода следственного действия путем воспроизведения.");
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc,
                "Протокол " + protocolName + " записан правильно, и его фонограмма и видеограмма отражает "
                        + "весь ход допроса и содержит полностью показания, данные в ходе допроса.");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc,
                "Заявлений, замечаний, дополнений, уточнений и исправлений, подлежащих внесению в протокол, не имею(ем).");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addInlineSignatureLine(doc, "Специалист(ы)", "");
        addInlineSignatureLine(doc, "Переводчик", "");
        addEmptyLine(doc);

        addInvestigatorBlock(doc, "Допросил, протокол составил:",
                data.getInvestigatorProfession(), formatFio(data.getInvestigator()));
    }

    /** Блок закрытия для specialist/expert */
    protected void addClosingSpecialist(XWPFDocument doc, CaseInterrogationFullResponse data,
                                        String roleLabel, String fio, boolean isDop) {
        String protocolName = isDop ? "дополнительного допроса" : "допроса";
        String label = isDop ? "На этом дополнительный допрос " : "На этом допрос ";
        String roleLower = roleLabel.toLowerCase();

        addJustifiedParagraph(doc,
                label + roleLower + " окончен. " + roleLabel
                        + " разъяснено право ознакомиться с результатами допроса, "
                        + "а также право внесения в протокол замечаний, дополнений, исправлений.");
        addEmptyLine(doc);

        String fam = safe(data.getFamiliarization()).equals("—") ? "личного прочтения" : data.getFamiliarization();
        addJustifiedParagraph(doc,
                roleLabel + " ознакомлен с протоколом " + protocolName + " путем (" + fam + ").");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc,
                "С протоколом " + protocolName + " ознакомлен(а). Записано правильно. "
                        + "Заявлений, замечаний, дополнений, исправлений, подлежащих внесению в протокол не имею.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addInvestigatorBlock(doc, "Допросил, протокол составил:",
                data.getInvestigatorProfession(), formatFio(data.getInvestigator()));
    }

    // ── Таблица персональных данных ──────────────────────────────────────────

    protected void addPersonDataTable(XWPFDocument doc, CaseInterrogationFullResponse data) {
        CaseInterrogationProtocolResponse p = data.getProtocol();
        String[][] rows = {
                {"Фамилия, имя, отчество:", safe(data.getFio())},
                {"Дата рождения:", p != null ? safe(p.getDateOfBirth()) : "—"},
                {"Место рождения:", p != null ? safe(p.getBirthPlace()) : "—"},
                {"Гражданство:", p != null ? safe(p.getCitizenship()) : "—"},
                {"Национальность:", p != null ? safe(p.getNationality()) : "—"},
                {"Образование:", formatEducations(p)},
                {"Семейное положение:", p != null ? safe(p.getMartialStatus()) : "—"},
                {"Место работы или учебы:", p != null ? safe(p.getWorkOrStudyPlace()) : "—"},
                {"Род занятий или должность:", p != null ? safe(p.getPosition()) : "—"},
                {"Место жительства и (или) регистрации:", p != null ? safe(p.getAddress()) : "—"},
                {"Контактные телефоны:", p != null ? safe(p.getContactPhone()) : "—"},
                {"Электронная почта:", p != null ? safe(p.getContactEmail()) : "—"},
                {"Иные контактные данные:", p != null ? safe(p.getOther()) : "—"},
                {"Отношение к подозреваемому, обвиняемому:", formatRelations(p)},
                {"Отношение к воинской обязанности:", formatMilitaries(p)},
                {"Наличие судимости:", formatCriminals(p)},
                {"Документ, удостоверяющий личность:",
                        p != null ? safe(p.getIinOrPassport()) : safe(data.getDocumentType()) + " " + safe(data.getNumber())},
                {"Применение технических средств аудио/видео фиксации:",
                        p != null ? safe(p.getTechnical()) : "Не применяются"}
        };
        buildDataTable(doc, rows);
    }

    protected void addPersonDataTableVictimDop(XWPFDocument doc, CaseInterrogationFullResponse data) {
        CaseInterrogationProtocolResponse p = data.getProtocol();
        String[][] rows = {
                {"Фамилия, имя, отчество:", safe(data.getFio())},
                {"Дата рождения:", p != null ? safe(p.getDateOfBirth()) : "—"},
                {"Место рождения:", p != null ? safe(p.getBirthPlace()) : "—"},
                {"Национальность:", p != null ? safe(p.getNationality()) : "—"},
                {"Образование:", formatEducations(p)},
                {"Гражданство:", p != null ? safe(p.getCitizenship()) : "—"},
                {"Семейное положение:", p != null ? safe(p.getMartialStatus()) : "—"},
                {"Судимость:", formatCriminals(p)},
                {"Место жительства:", p != null ? safe(p.getAddress()) : "—"},
                {"Место работы (учебы):", p != null ? safe(p.getWorkOrStudyPlace()) : "—"},
                {"Отношение к воинской обязанности:", formatMilitaries(p)},
                {"Паспорт или иной документ, удостоверяющий личность:",
                        p != null ? safe(p.getIinOrPassport()) : safe(data.getDocumentType()) + " " + safe(data.getNumber())},
                {"Телефон:", p != null ? safe(p.getContactPhone()) : "—"},
                {"Электронная почта:", p != null ? safe(p.getContactEmail()) : "—"},
                {"Иные данные о личности потерпевшего:", p != null ? safe(p.getOther()) : "—"},
                {"Иные участвующие лица:", safe(data.getInvolved())},
                {"Применение технических средств аудио/видео фиксации:",
                        p != null ? safe(p.getTechnical()) : "Не применяются"},
                {"Отношение потерпевшего к подозреваемому:",formatRelations(p)}
        };
        buildDataTable(doc, rows);
    }

    // ── Тексты статей УПК РК (русский) ───────────────────────────────────────

    public static final String RIGHTS_ST71 =
            "Потерпевший имеет право:\n"
                    + "1) знать о предъявленном подозрении и обвинении; "
                    + "2) давать показания на родном языке или языке, которым владеет; "
                    + "3) представлять доказательства; "
                    + "4) заявлять ходатайства и отводы; "
                    + "5) пользоваться бесплатной помощью переводчика; "
                    + "6) иметь представителя; "
                    + "7) получать имущество, изъятое у него органом уголовного преследования в качестве средства доказывания; "
                    + "8) примириться, в том числе в порядке медиации, с подозреваемым, обвиняемым в случаях, предусмотренных законом; "
                    + "8-1) выразить согласие на применение приказного производства; "
                    + "9) знакомиться с протоколами следственных действий, производимых с его участием, и подавать на них замечания; "
                    + "10) участвовать с разрешения следователя или дознавателя в следственных действиях; "
                    + "11) знакомиться по окончании досудебного расследования со всеми материалами дела; "
                    + "12) заявлять ходатайства о предоставлении мер безопасности ему и членам его семьи; "
                    + "13) получить копии постановлений о признании его потерпевшим или отказе в этом; "
                    + "14) участвовать в судебном разбирательстве дела; "
                    + "15) выступать в судебных прениях; "
                    + "16) поддерживать обвинение; "
                    + "17) знакомиться с протоколом судебного заседания и подавать замечания; "
                    + "18) приносить жалобы на действия (бездействие) органа, ведущего уголовный процесс; "
                    + "19) обжаловать приговор и постановления суда; "
                    + "20) знать о принесенных по делу жалобах и протестах; "
                    + "21) защищать свои права и законные интересы иными способами, не противоречащими закону; "
                    + "22) знать о намерении сторон заключить процессуальное соглашение; "
                    + "23) на получение компенсации в соответствии с законодательством РК о Фонде компенсации потерпевшим.\n\n"
                    + "Потерпевший обязан: явиться по вызову органа, ведущего уголовный процесс, правдиво сообщить все известные "
                    + "по делу обстоятельства и ответить на поставленные вопросы; не разглашать сведения об обстоятельствах, "
                    + "известных ему по делу; соблюдать установленный порядок при производстве следственных действий и во время судебного заседания.\n\n"
                    + "При неявке потерпевшего по вызову без уважительных причин он может быть подвергнут принудительному приводу "
                    + "в порядке, предусмотренном статьей 157 УПК, и на него может быть наложено денежное взыскание "
                    + "в порядке, предусмотренном статьей 160 УПК.\n\n"
                    + "За отказ от дачи показаний и дачу заведомо ложных показаний потерпевший несет уголовную ответственность.\n\n"
                    + "Потерпевшему(ей) разъяснено право отказаться от дачи показаний, уличающих в совершении уголовного правонарушения "
                    + "его самого, супруга (супруги), близких родственников.";

    public static final String RIGHTS_ST71_FULL =
            "1. Потерпевшим в уголовном процессе признается лицо, в отношении которого есть основание полагать, что ему "
                    + "непосредственно уголовным правонарушением причинен моральный, физический или имущественный вред.\n\n"
                    + "6. Потерпевший имеет право: 1) знать о предъявленном подозрении и обвинении; "
                    + "2) давать показания на родном языке или языке, которым владеет; 3) представлять доказательства; "
                    + "4) заявлять ходатайства и отводы; 5) пользоваться бесплатной помощью переводчика; 6) иметь представителя; "
                    + "7) получать имущество; 8) примириться с подозреваемым; 8-1) выразить согласие на применение приказного производства; "
                    + "9) знакомиться с протоколами следственных действий; 10) участвовать в следственных действиях; "
                    + "11) знакомиться с материалами дела; 12) заявлять ходатайства о мерах безопасности; "
                    + "13) получить копии постановлений; 14) участвовать в судебном разбирательстве; "
                    + "15) выступать в судебных прениях; 16) поддерживать обвинение; "
                    + "17) знакомиться с протоколом судебного заседания; "
                    + "18) приносить жалобы; 19) обжаловать приговор; 20) знать о жалобах и протестах; "
                    + "21) защищать свои права; 22) знать о намерении заключить процессуальное соглашение; "
                    + "23) на получение компенсации.\n\n"
                    + "8. Потерпевший обязан: явиться по вызову, правдиво сообщить все известные обстоятельства, "
                    + "не разглашать сведения, соблюдать установленный порядок.\n\n"
                    + "За отказ от дачи показаний и дачу заведомо ложных показаний потерпевший несет уголовную ответственность.";

    public static final String RIGHTS_ST78 =
            "Свидетель имеет право:\n"
                    + "1) отказаться от дачи показаний, которые могут повлечь для него самого, его супруга (супруги) или близких "
                    + "родственников преследование за совершение уголовно наказуемого деяния или административного правонарушения; "
                    + "2) давать показания на своем родном языке или языке, которым владеет; "
                    + "3) пользоваться бесплатной помощью переводчика; "
                    + "4) заявлять отвод переводчику; "
                    + "5) собственноручной записи показаний в протоколе допроса; "
                    + "6) приносить жалобы на действия дознавателя, следователя, прокурора и суда, заявлять ходатайства, "
                    + "в том числе о принятии мер безопасности.\n\n"
                    + "Свидетель имеет право давать показания в присутствии своего адвоката.\n\n"
                    + "Свидетелю обеспечивается возмещение расходов, понесенных им при производстве по уголовному делу.\n\n"
                    + "Свидетель обязан:\n"
                    + "1) явиться по вызову дознавателя, следователя, прокурора и суда; "
                    + "2) правдиво сообщить все известное по делу и ответить на поставленные вопросы; "
                    + "3) не разглашать сведения об обстоятельствах, известных ему по делу, если он был предупрежден об этом; "
                    + "4) соблюдать установленный порядок при производстве следственных действий и во время судебного заседания.\n\n"
                    + "За дачу ложных показаний, отказ от дачи показаний свидетель несет уголовную ответственность, "
                    + "предусмотренную ст.ст.420, 421 УК РК.\n\n"
                    + "Свидетелю разъяснено право отказаться от дачи показаний, уличающих в совершении уголовного правонарушения "
                    + "его самого, супруга (супруги), близких родственников.";

    public static final String RIGHTS_ST64 =
            "Подозреваемый имеет право:\n"
                    + "1) получить разъяснение принадлежащих прав; 2) знать, в чем подозревается; "
                    + "3) пригласить защитника самостоятельно или через родственников; "
                    + "4) пользоваться правами гражданского ответчика в случае признания таковым; "
                    + "5) иметь свидание с защитником наедине и конфиденциально, в том числе до начала допроса; "
                    + "6) давать показания только в присутствии защитника; "
                    + "7) получить копии постановлений о признании подозреваемым; "
                    + "8) отказаться от дачи показаний; "
                    + "9) получить разъяснение о порядке и условиях применения меры пресечения; "
                    + "10) представлять доказательства; 11) заявлять ходатайства и отводы; "
                    + "12) давать показания на родном языке или языке, которым владеет; "
                    + "13) пользоваться бесплатной помощью переводчика; "
                    + "14) участвовать с разрешения органа уголовного преследования в следственных действиях; "
                    + "15) примириться с потерпевшим в случаях, предусмотренных законом; "
                    + "16) заявить ходатайство прокурору о заключении процессуального соглашения; "
                    + "16-1) заявить ходатайство о применении приказного производства; "
                    + "17) знакомиться с протоколами следственных действий и подавать замечания; "
                    + "18) приносить жалобы на действия (бездействие) следователя, дознавателя, прокурора и суда; "
                    + "19) защищать свои права и законные интересы иными способами, не противоречащими закону; "
                    + "20) при назначении и производстве экспертизы осуществлять действия, предусмотренные ст.274, 286 УПК РК; "
                    + "21) знакомиться по окончании расследования с материалами дела; "
                    + "22) возражать против прекращения уголовного преследования; "
                    + "23) безотлагательно уведомляться о принятии процессуальных решений; "
                    + "24) ходатайствовать о дополнительном допросе показывающего против него свидетеля.\n\n"
                    + "Подозреваемый имеет право отказаться от дачи показаний до начала первого допроса.";

    public static final String RIGHTS_ST65_1 =
            "Свидетель, имеющий право на защиту, имеет право:\n"
                    + "1) получить разъяснение принадлежащих прав; "
                    + "2) получить разъяснение о статусе свидетеля, имеющего право на защиту; "
                    + "3) ознакомиться с постановлением о назначении экспертизы; "
                    + "4) ознакомиться с заключением экспертизы; "
                    + "5) отказаться от дачи показаний; "
                    + "6) пригласить защитника самостоятельно или через родственников; "
                    + "7) давать показания в присутствии защитника; "
                    + "8) давать показания на родном языке или языке, которым владеет; "
                    + "9) пользоваться бесплатной помощью переводчика; "
                    + "10) собственноручной записи своих показаний в протоколе допроса; "
                    + "11) знакомиться с документами, за исключением материалов оперативно-розыскных мероприятий; "
                    + "12) знакомиться с протоколами следственных действий и подавать на них замечания; "
                    + "13) заявлять ходатайства, касающиеся его прав и законных интересов; "
                    + "14) заявлять отводы; "
                    + "15) на очную ставку с теми, кто свидетельствует против него; "
                    + "16) приносить жалобы на действия (бездействие) дознавателя, следователя, прокурора.\n\n"
                    + "Свидетель, имеющий право на защиту, обязан: являться по вызовам суда, прокурора, "
                    + "лица, осуществляющего досудебное расследование; соблюдать установленный порядок при "
                    + "производстве следственных действий и во время судебного заседания.\n\n"
                    + "Права и обязанности разъясняются перед началом первого процессуального действия с его участием.\n\n"
                    + "Если свидетель, имеющий право на защиту, не воспользовался своим правом отказаться от дачи показаний "
                    + "до начала первого допроса, он должен быть предупрежден о том, что его показания могут быть использованы "
                    + "в качестве доказательств в уголовном процессе.\n\n"
                    + "За неявку без уважительных причин по вызову органа, ведущего уголовный процесс, на свидетеля, "
                    + "имеющего право на защиту, может быть наложено денежное взыскание.";

    public static final String RIGHTS_ST80_SPECIALIST =
            "Специалист имеет право:\n"
                    + "1) знакомиться с материалами, относящимися к предмету исследования; "
                    + "2) заявлять ходатайства о предоставлении дополнительных материалов; "
                    + "3) знать цель своего вызова; "
                    + "4) отказаться от участия в производстве по делу, если не обладает соответствующими специальными знаниями; "
                    + "5) с разрешения органа, ведущего уголовный процесс, задавать вопросы участникам следственного действия; "
                    + "6) проводить исследование материалов дела с отражением хода и результатов в протоколе; "
                    + "7) знакомиться с протоколом следственного действия и делать замечания; "
                    + "8) приносить жалобы на действия органа, ведущего уголовный процесс; "
                    + "9) пользоваться бесплатной помощью переводчика; "
                    + "10) заявлять отвод переводчику; "
                    + "11) заявлять ходатайство о принятии мер безопасности; "
                    + "12) получать возмещение расходов и вознаграждение за выполненную работу.\n\n"
                    + "Специалист не вправе:\n"
                    + "1) вести переговоры с участниками процесса без ведома органа, ведущего уголовный процесс; "
                    + "2) самостоятельно собирать материалы исследования.\n\n"
                    + "Специалист обязан:\n"
                    + "1) явиться по вызову органа, ведущего уголовный процесс; "
                    + "2) участвовать в производстве следственных действий, используя специальные знания; "
                    + "3) давать пояснения по поводу выполняемых действий; "
                    + "4) не разглашать сведения об обстоятельствах дела; "
                    + "5) соблюдать порядок при производстве следственных действий; "
                    + "6) обеспечить сохранность представленных на исследование объектов.\n\n"
                    + "За отказ или уклонение от выполнения обязанностей без уважительных причин на специалиста "
                    + "может быть наложено денежное взыскание (ст.160 УПК). "
                    + "В случае заведомо ложного заключения специалист несет уголовную ответственность.";

    public static final String RIGHTS_ST82_EXPERT =
            "Эксперт имеет право:\n"
                    + "1) знакомиться с материалами дела, относящимися к предмету экспертизы; "
                    + "2) заявлять ходатайства о предоставлении ему дополнительных материалов, необходимых для дачи заключения; "
                    + "3) с разрешения органа, ведущего уголовный процесс, участвовать в производстве следственных и судебных действий, "
                    + "задавать вопросы участникам следственного действия; "
                    + "4) знакомиться с протоколом следственного или судебного действия, в котором он участвовал, "
                    + "а также в соответствующей части с протоколом судебного заседания и делать замечания; "
                    + "5) указывать в заключении на обстоятельства, имеющие значение для дела, по поводу которых ему не были поставлены вопросы; "
                    + "6) приносить жалобы на действия органа, ведущего уголовный процесс; "
                    + "7) пользоваться бесплатной помощью переводчика; "
                    + "8) заявлять отвод переводчику; "
                    + "9) заявлять ходатайство о принятии мер безопасности; "
                    + "10) получать возмещение расходов и вознаграждение за выполненную работу, если участие в производстве "
                    + "по делу не входит в круг его должностных обязанностей.\n\n"
                    + "Эксперт не вправе:\n"
                    + "1) вступать в контакт с участниками процесса по вопросам, связанным с проведением экспертизы, "
                    + "без ведома органа, ведущего уголовный процесс; "
                    + "2) самостоятельно собирать материалы для экспертного исследования; "
                    + "3) проводить исследования, которые могут повлечь полное уничтожение объектов либо изменение их внешнего вида "
                    + "или основных свойств, без разрешения органа, ведущего уголовный процесс.\n\n"
                    + "Эксперт обязан:\n"
                    + "1) явиться по вызову органа, ведущего уголовный процесс; "
                    + "2) провести полное исследование и дать обоснованное и объективное письменное заключение по поставленным вопросам; "
                    + "3) отказаться от дачи заключения и сообщить об этом органу, ведущему уголовный процесс, "
                    + "если поставленные вопросы выходят за пределы его специальных знаний или если предоставленные материалы недостаточны; "
                    + "4) не разглашать сведения об обстоятельствах дела и иные сведения, "
                    + "ставшие ему известными в связи с проведением экспертизы; "
                    + "5) обеспечить сохранность представленных на экспертизу объектов; "
                    + "6) соблюдать установленный порядок при производстве следственных действий и во время судебного заседания.\n\n"
                    + "За дачу заведомо ложного заключения эксперт несет уголовную ответственность, установленную законом.\n\n"
                    + "За отказ или уклонение от выполнения своих обязанностей без уважительных причин на эксперта "
                    + "может быть наложено денежное взыскание в порядке, установленном статьей 160 УПК РК.";
}