package org.di.digital.service.export.interrogation.kz;

import org.apache.poi.xwpf.usermodel.*;
import org.di.digital.dto.response.interrogation.CaseInterrogationApplicationFileResponse;
import org.di.digital.dto.response.interrogation.CaseInterrogationFullResponse;
import org.di.digital.dto.response.interrogation.CaseInterrogationProtocolResponse;
import org.di.digital.dto.response.interrogation.InvolvedPersonsResponse;
import org.di.digital.model.user.User;
import org.di.digital.service.export.interrogation.BaseInterrogationDocBuilder;
import org.di.digital.util.LocalizationHelper;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Базовый класс для всех казахскоязычных протоколов.
 * Содержит:
 *  - форматирование даты/времени на казахском
 *  - общие блоки (шапка, участники, закрытие, следователь)
 *  - полные тексты статей УПК РК на казахском
 */
public abstract class KazBaseBuilder extends BaseInterrogationDocBuilder {

    private static final DateTimeFormatter DATE_KZ =
            DateTimeFormatter.ofPattern("«d» MMMM yyyy 'жыл'", new Locale("kk"));

    protected final LocalizationHelper localizationHelper;

    protected KazBaseBuilder(LocalizationHelper localizationHelper) {
        this.localizationHelper = localizationHelper;
    }

    // ── Форматирование времени ────────────────────────────────────────────────

    @Override
    protected String formatTime(LocalDateTime dt) {
        return "«" + String.format("%02d", dt.getHour())
                + "» сағат «" + String.format("%02d", dt.getMinute()) + "» минутта";
    }

    @Override
    protected String getSignatureNote() {
        return "(қолы)";
    }

    protected String formatDateKaz(java.time.LocalDate date) {
        return date != null ? date.format(DATE_KZ) : "«___» ________ ____ жыл";
    }

    // ── Шапка (дата слева, город справа) + время ─────────────────────────────

    /** Стандартная шапка: дата / город + время (начало + конец) */
    protected void addCityDateBlockKaz(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String dateStr = formatDateKaz(data.getDate());
        addHeaderRow(doc, dateStr, safe(data.getCity()));
        addEmptyLine(doc);

        boolean multiDay = hasMultipleDays(data.getTimerSessions(), data.getFinishedAt());
        var sessions = data.getTimerSessions();
        XWPFParagraph timePara = doc.createParagraph();
        timePara.setAlignment(ParagraphAlignment.LEFT);
        if (sessions != null && !sessions.isEmpty()) {
            var first = sessions.get(0);
            String start = formatTimeWithDateIfNeededKaz(first.getStartedAt(), multiDay);
            String end = formatTimeWithDateIfNeededKaz(data.getFinishedAt(), multiDay);
            XWPFRun r1 = timePara.createRun();
            r1.setText("Жауап алу " + start + " басталып,");
            r1.setFontFamily(FONT); r1.setFontSize(FONT_SIZE); r1.addBreak();
            XWPFRun r2 = timePara.createRun();
            r2.setText(end + " аяқталды.");
            r2.setFontFamily(FONT); r2.setFontSize(FONT_SIZE);
        } else {
            XWPFRun r = timePara.createRun();
            r.setText("Жауап алу «__» сағат «__» минутта басталып,\n«__» сағат «__» минутта аяқталды.");
            r.setFontFamily(FONT); r.setFontSize(FONT_SIZE);
        }
        addEmptyLine(doc);
    }

    protected String formatTimeWithDateIfNeededKaz(LocalDateTime dt, boolean showDate) {
        if (dt == null) return "«__» сағат «__» минутта";
        if (showDate) {
            return dt.toLocalDate().format(DATE_KZ) + " " + formatTime(dt);
        }
        return formatTime(dt);
    }
    /** Расширенный формат с перерывами */
    protected void addCityDateBlockKazExtended(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String dateStr = formatDateKaz(data.getDate());
        addHeaderRow(doc, dateStr, safe(data.getCity()));
        addEmptyLine(doc);

        boolean multiDay = hasMultipleDays(data.getTimerSessions(), data.getFinishedAt());
        var sessions = data.getTimerSessions();
        boolean hasRealPause = sessions != null && sessions.size() > 1;

        XWPFParagraph timePara = doc.createParagraph();
        timePara.setAlignment(ParagraphAlignment.LEFT);
        if (sessions != null && !sessions.isEmpty()) {
            var first = sessions.get(0);
            String start = formatTimeWithDateIfNeededKaz(first.getStartedAt(), multiDay);
            XWPFRun r1 = timePara.createRun();
            r1.setText("Жауап алу " + start + " басталып,");
            r1.setFontFamily(FONT); r1.setFontSize(FONT_SIZE); r1.addBreak();

            String paused = (hasRealPause && sessions.get(0).getPausedAt() != null)
                    ? formatTimeWithDateIfNeededKaz(sessions.get(0).getPausedAt(), multiDay)
                    : "«__» сағат «__» минутта";
            XWPFRun r2 = timePara.createRun();
            r2.setText("уақытша " + paused + " тоқтатылып,");
            r2.setFontFamily(FONT); r2.setFontSize(FONT_SIZE); r2.addBreak();

            String end = formatTimeWithDateIfNeededKaz(data.getFinishedAt(), multiDay);
            XWPFRun r3 = timePara.createRun();
            r3.setText(end + " аяқталды.");
            r3.setFontFamily(FONT); r3.setFontSize(FONT_SIZE);
        } else {
            XWPFRun r = timePara.createRun();
            r.setText("Жауап алу «__» сағат «__» минутта басталып,\n"
                    + "уақытша «__» сағат «__» минутта тоқтатылып,\n«__» сағат «__» минутта аяқталды.");
            r.setFontFamily(FONT); r.setFontSize(FONT_SIZE);
        }
    }

    /** Упрощённый формат для специалиста/эксперта (Начало + Окончание с перерывами) */
    protected void addCityDateBlockKazSimple(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String dateStr = formatDateKaz(data.getDate());
        addHeaderRow(doc, dateStr, safe(data.getCity()));
        addEmptyLine(doc);
        addTimeBlock(doc,
                data.getTimerSessions(), data.getFinishedAt(),
                ParagraphAlignment.LEFT, false,
                "Басталуы",
                "Тоқтатылды (ҚР ҚПК-нің 209-бабына сәйкес)",
                "Жалғастырылды (ҚР ҚПК-нің 209-бабына сәйкес)",
                "Аяқталуы",
                "«__» сағат «__» минутта",
                "«__» сағат «__» минутта");
    }

    // ── Вводный текст ─────────────────────────────────────────────────────────

    protected String buildIntroLineKaz(User user, CaseInterrogationFullResponse data) {
        return localizationHelper.getLocalizedInterrogation(user.getProfession(), data.getLanguage())
                + " " + localizationHelper.getLocalizedInterrogation(user.getAdministration(), data.getLanguage())
                + " " + localizationHelper.getLocalizedInterrogation(user.getRegion(), data.getLanguage())
                + " бойынша " + safe(data.getRoom()) + " қызметтік бөлмесінде, "
                + safe(data.getAddrezz()) + " мекенжайы бойынша орналасқан әкімшілік ғимаратта";
    }

    // ── Участники ─────────────────────────────────────────────────────────────

    protected void addInvolvedBlockKaz(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String involved = safe(data.getInvolved()).equals("—") ? "басқа тұлғалар тартылмады" : data.getInvolved();
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun r1 = p.createRun();
        r1.setText("Жауап алу барысында   ");
        r1.setFontFamily(FONT); r1.setFontSize(FONT_SIZE);
        XWPFRun r2 = p.createRun();
        r2.setUnderline(UnderlinePatterns.SINGLE);
        r2.setText(involved);
        r2.setFontFamily(FONT); r2.setFontSize(FONT_SIZE);
        XWPFRun r3 = p.createRun();
        r3.setText(".");
        r3.setFontFamily(FONT); r3.setFontSize(FONT_SIZE);
        addEmptyLine(doc);
    }

    protected void addInvolvedSpecialistBlockKaz(XWPFDocument doc, CaseInterrogationFullResponse data) {
        String involved = safe(data.getInvolved()).equals("—") ? "тартылмады" : data.getInvolved();
        addJustifiedParagraph(doc,
                "Қосымша жауап алу барысында маман(дар) ретінде: " + involved
                        + ", аудармашы ретінде: " + safe(data.getTranslator()) + " тартылды.");
    }

    protected void addSpecialistTranslatorRightsKaz(XWPFDocument doc) {
        addJustifiedParagraph(doc, "Маманға ҚР ҚПК-нің 80, 110-баптарына сәйкес оның құқықтары мен міндеттері түсіндірілді.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Маман(дар)", "");
        addEmptyLine(doc);
        addJustifiedParagraph(doc, "Аудармашыға ҚР ҚПК-нің 81, 110-баптарына сәйкес оның құқықтары мен міндеттері түсіндірілді.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Аудармашы", "");
        addEmptyLine(doc);
    }

    // ── Язык показаний ───────────────────────────────────────────────────────

    protected void addLanguageBlockKaz(XWPFDocument doc, CaseInterrogationFullResponse data,
                                       String roleLabel, String fio) {
        String language = safe(data.getLanguage()).equals("—") ? "қазақ" : data.getLanguage();
        String translator = safe(data.getTranslator()).equals("—") ? "қажет етпеймін" : data.getTranslator();
        addJustifiedItalicParagraph(doc,
                "Мен, " + fio + ", жауап беруді " + language + " тілінде қалаймын, "
                        + "аудармашының көмегін " + translator + ".");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);
    }

    // ── Права по статьям ─────────────────────────────────────────────────────

    protected void addRightsSectionKaz(XWPFDocument doc, String roleLabel, String fio,
                                       String article, String rightsText) {
        addJustifiedParagraph(doc,
                roleLabel + " " + fio + " өзіне ҚР ҚПК-нің "
                        + article + "-бабымен көзделген құқықтары мен міндеттері түсіндірілді, атап айтқанда:");
        addEmptyLine(doc);
        addJustifiedMultilineParagraph(doc, rightsText);
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc,
                "ҚР ҚПК-нің " + article + "-бабымен көзделген құқықтар мен міндеттер маған түсіндірілді. Құқықтардың мәні түсінікті.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);
    }

    // ── Закрывающие блоки ────────────────────────────────────────────────────
    protected void addInvolvedPersonsSignaturesKaz(XWPFDocument doc, CaseInterrogationFullResponse data) {
        if (data.getInvolvedPersons() == null || data.getInvolvedPersons().isEmpty()) return;
        for (InvolvedPersonsResponse person : data.getInvolvedPersons()) {
            String roleLabel = safe(person.getType());
            String fio = person.getAbout() != null && !person.getAbout().isBlank()
                    ? person.getAbout() : "";
            addInlineSignatureLine(doc, roleLabel, fio);
        }
        addEmptyLine(doc);
    }
    protected void addInlineSignatureLineWithLawyerKaz(XWPFDocument doc, String roleLabel, String fio,
                                                       String lawyer) {
        addInlineSignatureLine(doc, roleLabel, fio);

        if (lawyer != null && !lawyer.isBlank()) {
            addInlineSignatureLine(doc, "Қорғаушы (адвокат)", lawyer);
        }
    }

    protected void addClosingSectionKaz(XWPFDocument doc, CaseInterrogationFullResponse data,
                                        String roleGenitive, String roleLabel, String fio) {
        addJustifiedParagraph(doc,
                "Осымен " + roleGenitive + " жауап алу аяқталды. "
                        + "Жауап алуға қатысушыларға жауап алу нәтижелерімен танысу, сондай-ақ хаттамаға "
                        + "өтініштер, ескертулер, толықтырулар, нақтылаулар мен түзетулер енгізу құқықтары түсіндірілді.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Тергеу әрекетіне қатысушыларға " + roleGenitive + " жауап алу хаттамасы танысу үшін ұсынылды.");
        addEmptyLine(doc);

        XWPFParagraph famPara = doc.createParagraph();
        famPara.setAlignment(ParagraphAlignment.BOTH);
        famPara.setIndentationFirstLine(720);
        XWPFRun famLabel = famPara.createRun();
        famLabel.setText("Осы жауап алу хаттамасымен   ");
        famLabel.setFontFamily(FONT); famLabel.setFontSize(FONT_SIZE);
        XWPFRun famValue = famPara.createRun();
        famValue.setUnderline(UnderlinePatterns.SINGLE);
        famValue.setText(safe(data.getFamiliarization()).equals("—") ? "жария ету жолымен" : data.getFamiliarization());
        famValue.setFontFamily(FONT); famValue.setFontSize(FONT_SIZE);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Жауап алу хаттамасы дұрыс жазылды және жауап алудың барлық барысын көрсетеді және "
                        + "жауап алу барысында берілген айғақтарды толық қамтиды.");
        addEmptyLine(doc);

        XWPFParagraph zaPara = doc.createParagraph();
        zaPara.setAlignment(ParagraphAlignment.BOTH);
        zaPara.setIndentationFirstLine(720);
        XWPFRun zaLabel = zaPara.createRun();
        zaLabel.setText("Хаттамаға енгізуге жататын өтініштер, ескертулер, толықтырулар, нақтылаулар мен түзетулер ");
        zaLabel.setFontFamily(FONT); zaLabel.setFontSize(FONT_SIZE);
        XWPFRun zaValue = zaPara.createRun();
        zaValue.setUnderline(UnderlinePatterns.SINGLE);
        zaValue.setText(safe(data.getAdditionalInfo()).equals("—") ? "жоқ" : data.getAdditionalInfo());
        zaValue.setFontFamily(FONT); zaValue.setFontSize(FONT_SIZE);
        addEmptyLine(doc);

        if (StringUtils.hasText(data.getAdditionalInfo())) {
            addJustifiedParagraph(doc, data.getAdditionalText());
            addEmptyLine(doc);
        }

        XWPFParagraph appPara = doc.createParagraph();
        appPara.setAlignment(ParagraphAlignment.BOTH);
        appPara.setIndentationFirstLine(720);
        XWPFRun appLabel = appPara.createRun();
        appLabel.setText("Хаттамаға ");
        appLabel.setFontFamily(FONT); appLabel.setFontSize(FONT_SIZE);
        XWPFRun appValue = appPara.createRun();
        appValue.setUnderline(UnderlinePatterns.SINGLE);
        appValue.setText(safe(data.getApplication()).equals("—") ? "қоса берілмейді" : data.getApplication());
        appValue.setFontFamily(FONT); appValue.setFontSize(FONT_SIZE);
        addEmptyLine(doc);

        if (data.getApplications() != null && !data.getApplications().isEmpty()) {
            for (CaseInterrogationApplicationFileResponse file : data.getApplications()) {
                XWPFParagraph filePara = doc.createParagraph();
                filePara.setAlignment(ParagraphAlignment.BOTH);
                filePara.setIndentationFirstLine(720);
                setLineSpacing(filePara);
                XWPFRun fileRun = filePara.createRun();
                String pages = file.getPages() != null ? ", " + file.getPages() + " п." : "";
                fileRun.setText("- " + file.getDisplayName() + pages);
                fileRun.setFontFamily(FONT); fileRun.setFontSize(FONT_SIZE);
            }
            addEmptyLine(doc);
        }

        addEmptyLine(doc);

        addInvolvedPersonsSignaturesKaz(doc, data);
        addInlineSignatureLineWithLawyerKaz(doc, roleLabel, fio, data.getLawyer());

        addInvestigatorBlock(doc, "Жауап алды, хаттаманы жасады:",
                data.getInvestigatorProfession(), formatFio(data.getInvestigator()));
    }

    protected void addClosingSectionMultiPartyKaz(XWPFDocument doc, CaseInterrogationFullResponse data,
                                                  String protocolName, String roleLabel, String fio) {
        addJustifiedParagraph(doc,
                "Осымен " + protocolName + " аяқталды. "
                        + "Жауап алуға қатысушыларға жауап алу нәтижелерімен танысу, сондай-ақ хаттамаға "
                        + "өтініштер, ескертулер, толықтырулар, нақтылаулар мен түзетулер енгізу құқықтары түсіндірілді.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addInlineSignatureLine(doc, "Маман(дар)", "");
        addInlineSignatureLine(doc, "Аудармашы", "");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Тергеу әрекетіне қатысушыларға " + protocolName + " хаттамасы танысу үшін ұсынылды.");
        addEmptyLine(doc);

        String fam = safe(data.getFamiliarization()).equals("—")
                ? "жеке оқып шығу, қатысушының өтінісі бойынша жария ету" : data.getFamiliarization();
        addJustifiedParagraph(doc,
                "Осы жауап алу хаттамасымен " + fam + " жолымен, "
                        + "сондай-ақ тергеу әрекетінің барысын қайта көрсету жолымен фонограмма мен бейнеграммамен танысты.");
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc,
                protocolName + " хаттамасы дұрыс жазылды, оның фонограммасы мен бейнеграммасы "
                        + "жауап алудың барлық барысын көрсетеді және жауап алу барысында берілген айғақтарды толық қамтиды.");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc,
                "Хаттамаға енгізуге жататын өтініштер, ескертулер, толықтырулар, нақтылаулар мен түзетулер жоқ.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addInlineSignatureLine(doc, "Маман(дар)", "");
        addInlineSignatureLine(doc, "Аудармашы", "");
        addEmptyLine(doc);

        addInvolvedPersonsSignaturesKaz(doc, data);
        addInlineSignatureLineWithLawyerKaz(doc, roleLabel, fio, data.getLawyer());

        addInvestigatorBlock(doc, "Жауап алды, хаттаманы жасады:",
                data.getInvestigatorProfession(), formatFio(data.getInvestigator()));
    }

    /** Закрытие для специалиста/эксперта */
    protected void addClosingSpecialistKaz(XWPFDocument doc, CaseInterrogationFullResponse data,
                                           String roleLabel, String fio, boolean isDop) {
        String protocolName = isDop ? "қосымша жауап алу" : "жауап алу";
        String label = isDop ? "Осымен маманнан қосымша жауап алу аяқталды." : "Осымен маманнан жауап алу аяқталды.";

        addJustifiedParagraph(doc,
                label + " " + roleLabel + "ға жауап алу нәтижелерімен танысу, "
                        + "сондай-ақ хаттамаға ескертулер, толықтырулар, түзетулер енгізу құқығы түсіндірілді.");
        addEmptyLine(doc);

        String fam = safe(data.getFamiliarization()).equals("—") ? "жеке оқып шығу" : data.getFamiliarization();
        addJustifiedParagraph(doc,
                roleLabel + " " + protocolName + " хаттамасымен (" + fam + ") жолымен танысты.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc,
                (isDop ? "Қосымша жауап алу хаттамасымен" : "Жауап алу хаттамасымен")
                        + " таныстым. Дұрыс жазылды. "
                        + "Хаттамаға енгізуге жататын өтініштер, ескертулер, толықтырулар, түзетулер жоқ.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

        addInvestigatorBlock(doc, "Жауап алды, хаттаманы жасады:",
                data.getInvestigatorProfession(), formatFio(data.getInvestigator()));
    }

    // ── Таблица персональных данных (казахская) ───────────────────────────────

    protected void addPersonDataTableKaz(XWPFDocument doc, CaseInterrogationFullResponse data) {
        CaseInterrogationProtocolResponse p = data.getProtocol();
        String[][] rows = {
                {"Тегі, аты, әкесінің аты:", safe(data.getFio())},
                {"Туған күні:", p != null ? safe(p.getDateOfBirth()) : "—"},
                {"Туған жері:", p != null ? safe(p.getBirthPlace()) : "—"},
                {"Азаматтығы:", p != null ? safe(p.getCitizenship()) : "—"},
                {"Ұлты:", p != null ? safe(p.getNationality()) : "—"},
                {"Білімі:", formatEducations(p)},
                {"Отбасы жағдайы:", p != null ? safe(p.getMartialStatus()) : "—"},
                {"Жұмыс немесе оқу орны:", p != null ? safe(p.getWorkOrStudyPlace()) : "—"},
                {"Айналысу түрі немесе лауазымы:", p != null ? safe(p.getPosition()) : "—"},
                {"Тұратын жері және (немесе) тіркелген жері:", p != null ? safe(p.getAddress()) : "—"},
                {"Байланыс телефондары:", p != null ? safe(p.getContactPhone()) : "—"},
                {"Электрондық пошта:", p != null ? safe(p.getContactEmail()) : "—"},
                {"Басқа байланыс деректері:", p != null ? safe(p.getOther()) : "—"},
                {"Күдіктіге, айыпталушыға қатынасы:", formatRelations(p)},
                {"Әскери міндеттемеге қатынасы:", formatMilitaries(p)},
                {"Соттылығының болуы:", formatCriminals(p)},
                {"Жеке басын куәландыратын паспорт немесе басқа құжат:",
                        p != null ? safe(p.getIinOrPassport()) : safe(data.getDocumentType()) + " " + safe(data.getNumber())},
                {"Аудио/бейне тіркеу техникалық құралдарын қолдану:",
                        p != null ? safe(p.getTechnical()) : "Қолданылмайды"}
        };
        buildDataTable(doc, rows);
    }

    // ── Тексты статей УПК РК (казахский) ─────────────────────────────────────

    public static final String RIGHTS_ST78_KAZ =
            "Куәнiң:\n" +
                    "\n" +
                    "1) қылмыстық жазаланатын іс-әрекет немесе әкiмшiлiк құқық бұзушылық жасағаны үшiн оның өзiн, жұбайын (зайыбын) немесе жақын туыстарын қудалауға әкеп соғатын айғақтар беруден бас тартуға;\n" +
                    "2) өзiнiң ана тiлiнде немесе өзi бiлетiн тiлде айғақтар беруге;\n" +
                    "3) аудармашының тегiн көмегiн пайдалануға;\n" +
                    "4) өзінен жауап алуға қатысатын аудармашыға қарсылық білдіруді мәлімдеуге;\n" +
                    "5) жауап алу хаттамасына айғақтарды өз қолымен жазуға;\n" +
                    "6) анықтаушының, тергеушінің, прокурордың және соттың әрекеттерiне (әрекетсіздігіне) шағым келтіруге, өзінің құқықтары мен заңды мүдделерiне қатысты, оның ішінде қауіпсіздік шараларын қолдану туралы өтінішхаттар мәлімдеуге құқығы бар." +
                    "      \tКуәнің өз адвокатының қатысуымен айғақ беруге құқығы бар." +
                    " Куәның қылмыстық іс бойынша іс жүргізу кезінде шеккен шығыстарын оған өтеу қамтамасыз етіледі. " +
                    "Куә:\n" +
                    "1) анықтаушының, тергеушінің, прокурордың және соттың шақыруы бойынша келуге;\n" +
                    "2) іс бойынша болғанның бәрін шынайы түрде хабарлауға және қойылған сұрақтарға жауап беруге;\n" +
                    "3) егер өзіне бұл туралы анықтаушы, тергеуші немесе прокурор ескерткен болса, іс бойынша өзіне белгілі мән-жайлар туралы мәліметтерді жария етпеуге;\n" +
                    "4) тергеу әрекеттерін жүргізген кезде және сот отырысы уақытында белгіленген тәртіпті сақтауға міндетті. \n" +
                    "Куә жалған айғақтар бергенi, айғақтар беруден бас тартқаны үшін Қазақстан Республикасының Қылмыстық кодексiнде көзделген қылмыстық жауаптылықта болады. \n" +
                    "Айғақтар беруден жалтарғаны немесе қылмыстық процестi жүргізетін органның шақыруы бойынша дәлелді себептерсіз келмегені үшін куәға осы Кодекстің 160-бабында белгіленген тәртіппен ақшалай өндіріп алу қолданылуы мүмкін.";

    public static final String RIGHTS_ST71_KAZ =
            "Жәбірленушінің:\n" +
                    "1) келтірілген күдік пен тағылған айыптау туралы білуге;\n" +
                    "2) ана тілінде немесе өзі білетін тілде айғақ беруге;\n" +
                    "3) дәлелдемелерді ұсынуға;\n" +
                    "4) өтінішхаттар мен қарсылық білдірулерді мәлімдеуге;\n" +
                    "5) аудармашының тегін көмегін пайдалануға;\n" +
                    "6) өкілінің болуына;\n" +
                    "7) қылмыстық қудалау органы одан дәлелдеу құралы ретінде алып қойған немесе өзі ұсынған мүлікті, сондай-ақ қылмыстық заңмен тыйым салынған іс-әрекетті жасаған адамнан алып қойылған, өзіне тиесілі мүлікті алуға, өзіне тиесілі құжаттардың төлнұсқаларын алуға;\n" +
                    "8) заңда көзделген жағдайларда күдіктімен, айыпталушымен, сотталушымен татуласуға, оның ішінде медиация тәртібімен татуласуға;\n" +
                    "8-1) қылмыстық теріс қылық немесе онша ауыр емес қылмыс туралы іс бойынша бұйрықтық іс жүргізуді қолдануға келісім білдіруге;\n" +
                    "9) өзінің қатысуымен жүргізілген тергеу әрекеттерінің хаттамаларымен танысуға және оларға ескертулер беруге, ғылыми-техникалық құралдарды қолдана отырып, хаттамалардың көшірмелерін түсіруге және алуға;\n" +
                    "10) өз өтінішхаты не өз өкілінің өтінішхаты бойынша жүргізілетін тергеу әрекеттеріне тергеушінің немесе анықтаушының рұқсатымен қатысуға;\n" +
                    "11) мемлекеттік құпияларды немесе заңмен қорғалатын өзге де құпияны құрайтын мәліметтерді қоспағанда, тергеп-тексеру аяқталғаннан кейін осы Кодексте белгіленген тәртіппен істің материалдарымен танысуға және одан кез келген мәліметтерді жазып алуға, сондай-ақ ғылыми-техникалық құралдарды пайдалана отырып, көшірмелерін түсіруге;\n" +
                    "12) өзіне және өзінің отбасы мүшелеріне қауіпсіздік шараларын ұсыну, жеке өмірінің мән-жайларын жария етпеу туралы, күдіктіге қатысты жақындауға тыйым салуды қолдану туралы өтінішхаттар мәлімдеуге;\n" +
                    "13) өзін жәбірленуші деп тану немесе одан бас тарту, сотқа дейінгі тергеп-тексеруді тоқтату туралы қаулылардың, айыптау актісінің, сотқа дейінгі жеделдетілген тергеп-тексеру хаттамасының, айыптау хаттамасының көшірмелерін, сондай-ақ бірінші, апелляциялық және кассациялық сатылардағы соттың үкімі мен қаулысының көшірмелерін алуға;\n" +
                    "14) бірінші, апелляциялық және кассациялық сатылардағы сотта істі соттың талқылауына қатысуға;\n" +
                    "15) сот жарыссөздерінде сөйлеуге;\n" +
                    "16) айыптауды, оның ішінде мемлекеттік айыптаушы айыптаудан бас тартқан жағдайда да қолдауға;\n" +
                    "17) сот отырысының хаттамасымен танысуға, бұл ретте хаттаманың соңына өз қолын қоюға, ал сот отырысы хаттамасының бір бөлігімен танысқан кезде осы бөліктің соңына қол қоюға; ал сот отырысында аудио-, бейнетіркеу қолданылған жағдайда – хаттаманың соңына қол қоюға, хаттамаға ескертулер беруге құқылы;\n" +
                    "18) анықтаушының, тергеушінің, прокурордың және соттың әрекеттеріне (әрекетсіздігіне) және шешіміне шағым жасауға және заңда белгіленген жағдайларда оларды қарауға қатысуға;\n" +
                    "19) соттың үкімі мен қаулысына шағым жасауға;\n" +
                    "20) іс бойынша келтірілген шағымдар, прокурордың өтінішхаттары және наразылықтар туралы білуге, оларға қарсылықтар беруге және оларды қарауға қатысуға;\n" +
                    "21) өз құқықтары мен заңды мүдделерін заңға қайшы келмейтін өзге де тәсілдермен қорғауға;\n" +
                    "22) тараптардың процестік келісім жасасу ниеті, оның шарттары мен салдары туралы білуге, қылмыспен келтірілген залалды өтеу бойынша өз шарттарын ұсынуға не оны жасасуға қарсылық білдіруге;\n" +
                    "23) Қазақстан Республикасының Жәбірленушілерге өтемақы қоры туралы заңнамасына сәйкес өтемақы алуға;\n" +
                    "24) Қазақстан Республикасының адам саудасына қарсы іс-қимыл туралы заңнамасына сәйкес арнаулы әлеуметтік көрсетілетін қызметтерді алуға құқығы бар.\n" +
                    "                 Жәбiрленушi: қылмыстық процестi жүргізетін органның шақыруы бойынша келуге, iс бойынша барлық белгiлi мән-жайларды шынайы хабарлауға және қойылған сұрақтарға жауап беруге; өзiне iс бойынша белгiлi мән-жайлар туралы мәлiметтердi жария етпеуге; тергеу әрекеттерiн жүргiзу кезiнде және сот отырысы уақытында белгiленген тәртiптi сақтауға мiндеттi\n" +
                    "                 Жәбiрленушi шақыру бойынша дәлелді себептерсіз келмеген кезде ол осы Кодекстiң 157-бабында көзделген тәртiппен мәжбүрлеп әкелуге ұшырауы және оған осы Кодекстің 160-бабында көзделген тәртіппен ақшалай өндіріп алу қолданылуы мүмкiн.                 Жәбiрленушi айғақтар беруден бас тартқаны және көрінеу жалған айғақтар бергенi үшiн заңға сәйкес қылмыстық жауаптылықта болады.\n" +
                    "\n" +
                    "               Жәбірленушіге өзінің, жұбайының (зайыбының), жақын туыстарының қылмыстық құқық бұзушылық жасағанын әшкерелейтін айғақтар беруден бас тарту құқығы түсіндірілді.\n";

    public static final String RIGHTS_ST64_KAZ =
            "Күдікті:\n" +
                    "1) ұстап алуды жүзеге асырған адамнан өзіне тиесілі құқықтар туралы түсіндірме алуға;\n" +
                    "2) өзіне не үшін күдік келтірілгенін білуге;\n" +
                    "3) өз бетінше немесе өзінің туыстары немесе сенім білдірген адамдары арқылы қорғаушы шақыруға құқылы. Егер қорғаушыны күдікті, оның туыстары немесе сенім білдірген тұлғалары шақырмаса, қылмыстық қудалау органы осы Кодекстің 67-бабының үшінші бөлігінде көзделген тәртіппен қорғаушының қатысуын қамтамасыз етуге міндетті;\n" +
                    "4) іс бойынша азаматтық талап қойылуына байланысты өзін азаматтық жауапкер деп таныған жағдайда оның құқықтарын пайдалануға;\n" +
                    "5) таңдаған немесе тағайындалған қорғаушысымен, оның ішінде жауап алу басталғанға дейін оңаша және құпия кездесуге;\n" +
                    "6) күдікті қорғаушыдан бас тартқан жағдайларды қоспағанда, қорғаушысы қатысып отырған кезде ғана айғақ беруге;\n" +
                    "7) күдікті, азаматтық жауапкер деп тану туралы, іс-әрекетін саралау туралы қаулылардың, ұстап алу хаттамасының, бұлтартпау шарасын таңдау және оның мерзімін ұзарту туралы өтінішхаттың және қаулының, қылмыстық істі тоқтату туралы қаулының көшірмелерін алуға;\n" +
                    "8) айғақ беруден бас тартуға;\n" +
                    "9) сотқа дейінгі тергеп-тексеруді жүзеге асыратын адамнан кепіл түріндегі бұлтартпау шарасын және күзетпен ұстауға байланысты емес басқа шараларды қолдану тәртібі мен шарттары туралы түсіндірме алуға;\n" +
                    "10) дәлелдемелерді ұсынуға;\n" +
                    "11) өтінішхатты, оның ішінде қауіпсіздік шараларын қолдану туралы өтінішхатты және қарсылық білдірулерді мәлімдеуге;\n" +
                    "12) ана тілінде немесе өзі білетін тілде айғақ беруге;\n" +
                    "13) аудармашының тегін көмегін пайдалануға;\n" +
                    "13-1) өзіне қатысты сотқа дейінгі пробацияны жүргізу үшін пробация қызметіне жүгінуге;\n" +
                    "14) өз өтінішхаты бойынша немесе қорғаушысының не заңды өкілінің өтінішхаты бойынша жүргізілетін тергеу әрекеттеріне қылмыстық қудалау органының рұқсатымен қатысуға;\n" +
                    "15) заңда көзделген жағдайларда, оның ішінде медиация тәртібімен жәбірленушімен татуласуға;\n" +
                    "16) тергеп-тексерудің кез келген сатысында жазалаудың түрі мен шарасы туралы өз ұсыныстарын баяндай отырып, прокурорға процестік келісім жасасу туралы өтінішхат мәлімдеуге не оны жасасуға келісім білдіруге және процестік келісімді жасасуға;\n" +
                    "16-1) қылмыстық теріс қылық немесе онша ауыр емес қылмыс туралы іс бойынша бұйрықтық іс жүргізуді қолдану туралы өтінішхат мәлімдеуге;\n" +
                    "17) өзінің қатысуымен жүргізілген тергеу әрекеттерінің хаттамаларымен танысуға және хаттамаларға ескертулер беруге;\n" +
                    "18) тергеушінің, анықтаушының, прокурордың және соттың әрекеттеріне (әрекетсіздігіне) және шешімдеріне шағым келтіруге;\n" +
                    "19) өзінің құқықтары мен заңды мүдделерін заңға қайшы келмейтін өзге де тәсілдермен қорғауға;\n" +
                    "20) сараптама тағайындалған және жүргізілген кезде, сондай-ақ өзіне сарапшының қорытындысы ұсынылған кезде осы Кодекстің 274, 286-баптарында көзделген әрекеттерді жүзеге асыруға;\n" +
                    "21) тергеп-тексеру аяқталғаннан кейін осы Кодексте белгіленген тәртіппен іс материалдарымен танысуға және одан, мемлекеттік құпияларды немесе заңмен қорғалатын өзге де құпияны құрайтын мәліметтерді қоспағанда, кез келген мәліметтерді көшіріп алуға, сондай-ақ ғылыми-техникалық құралдарды пайдалана отырып, олардың көшірмелерін түсіруге;\n" +
                    "22) қылмыстық қудалауды тоқтатуға қарсылық білдіруге;\n" +
                    "23) жасырын тергеу әрекеттеріне қатысты мәселелерді қоспағанда, өзінің құқықтары мен заңды мүдделерін қозғайтын процестік шешімдердің қабылданғаны туралы өзін қылмыстық процесті жүргізетін органның кейінге қалдырмай хабардар етуіне, сондай-ақ олардың көшірмелерін алуға;\n" +
                    "24) өзіне қарсы айғақ берген куәден қосымша жауап алу туралы, өзі көрсеткен адамдарды куә ретінде шақыру және олардан жауап алу, олармен беттестіру туралы өтінішхат беруге құқылы.\n" +
                    "Күдіктінің алғашқы жауап алу басталғанға дейін айғақтар беруден бас тартуға құқығы бар.\n" +
                    "\n";
    public static final String RIGHTS_ST65_1_KAZ =
            " Қорғалуға құқығы бар куәнің:\n" +
                    "1) сотқа дейінгі іс жүргізуді жүзеге асыратын адамнан өзіне тиесілі құқықтар туралы түсіндірме алуға;\n" +
                    "2) сотқа дейінгі тергеп-тексеруді жүзеге асыратын адамнан қорғалуға құқығы бар куәнің мәртебесі туралы түсіндірме алуға;\n" +
                    "3) осы Кодексте көзделген жағдайларда сараптама тағайындау туралы қаулымен танысуға;\n" +
                    "4) осы Кодексте көзделген жағдайларда сараптаманың қорытындысымен танысуға;\n" +
                    "5) айғақтар беруден бас тартуға;\n" +
                    "6) өз бетінше немесе өзінің туыстары немесе сенім білдірілген адамдары арқылы қорғаушыны шақыруға құқығы бар. Егер қорғалуға құқығы бар куә, оның туыстары немесе сенім білдірілген адамдары қорғаушыны шақырмаса, қылмыстық қудалау органы осы Кодекстің 67-бабының үшінші бөлігінде көзделген тәртіппен оның қатысуын қамтамасыз етуге міндетті;\n" +
                    "7) қорғаушысының қатысуымен айғақтар беруге;\n" +
                    "8) ана тiлiнде немесе өзi білетін тiлде айғақтар беруге;\n" +
                    "9) аудармашының тегiн көмегiн пайдалануға;\n" +
                    "10) жауап алу хаттамасына өз айғақтарын өз қолымен жазуға;\n" +
                    "11) жедел-іздестіру, қарсы барлау іс-шараларының және жасырын тергеу әрекеттерінің материалдарын, сондай-ақ оларда қамтылған дербес деректерді қоспағанда, осы баптың бірінші бөлігінде көрсетілген құжаттармен танысуға;\n" +
                    "12) өзінің қатысуымен жүргізілген тергеу әрекеттерінің хаттамаларымен танысуға және оларға ескертулер беруге, дәлелдемелер ұсынуға;\n" +
                    "13) өзінің құқықтары мен заңды мүдделеріне қатысты, оның ішінде сараптама жүргізу және қауіпсіздік шараларын қолдану туралы өтінішхаттар мәлімдеуге;\n" +
                    "14) қарсылық білдірулерді мәлімдеуге;\n" +
                    "15) өзіне қарсы куә болғандармен беттесуге;\n" +
                    "16) анықтаушының, тергеушінің, прокурордың әрекеттеріне (әрекетсіздігіне) шағым келтіруге құқығы бар.\n" +
                    "Қорғалуға құқығы бар куә: соттың, прокурордың, сотқа дейінгі тергеп-тексеруді жүзеге асыратын адамның шақыруы бойынша келуге; тергеу әрекеттері жүргізілген кезде және сот отырысы уақытында белгіленген тәртіпті сақтауға міндетті.\n" +
                    "Қорғалуға құқығы бар куәнің құқықтары мен міндеттері оның қатысуымен болатын алғашқы процестік әрекет басталар алдында түсіндіріледі.\n" +
                    "Егер қорғалуға құқығы бар куә бірінші жауап алу басталғанға дейін айғақтар беруден бас тарту құқығын пайдаланбаса, оның айғақтары қылмыстық процесте, оның ішінде кейіннен осы айғақтардан бас тартқан кезде де дәлелдемелер ретінде пайдаланылуы мүмкін екендігі туралы оған ескертілуге тиіс.\n" +
                    "Қорғалуға құқығы бар куәге қылмыстық процесті жүргізетін органның шақыруы бойынша дәлелді себептерсіз келмегені үшін осы Кодекстің 160-бабында белгіленген тәртіппен ақшалай өндіріп алу қолданылуы мүмкiн.";

    public static final String RIGHTS_ST80_SPECIALIST_KAZ =
            " Маманның:\n" +
                    "1) зерттеу нысанасына жататын материалдармен танысуға;\n" +
                    "2) қорытынды беру үшін өзіне қажетті қосымша материалдар беру туралы өтінішхаттар мәлімдеуге;\n" +
                    "3) өзiнiң шақырылу мақсатын бiлуге;\n" +
                    "4) егер тиiстi арнаулы бiлiмi мен дағдысы болмаса, iс бойынша iс жүргiзуге қатысудан бас тартуға;\n" +
                    "5) тергеу немесе сот әрекетiне қатысушыларға қылмыстық процестi жүргiзетін органның рұқсатымен сұрақтар қоюға; олардың назарын дәлелдемелердi жинауға, зерттеу мен бағалауға және ғылыми-техникалық құралдарды қолдануға жәрдем көрсету, iстiң материалдарын зерттеу, сараптама тағайындауға материалдар дайындау кезiндегі өзiнiң әрекеттерiне байланысты мән-жайларға аударуға;\n" +
                    "6) қылмыстық процесті жүргізетін органның, соттың тағайындауы бойынша іс материалдарына, салыстырма зерттеулердi қоспағанда, оның барысы мен нәтижелерін осы Кодекстiң 199-бабының тоғызыншы бөлiгiнде көзделген тәртiппен қылмыстық iске қосып тігілетін хаттамада не ресми құжатта көрсете отырып, объектiлердiң толық немесе iшiнара жойылуына не олардың сыртқы түрiнің немесе негiзгi қасиеттерiнiң өзгеруiне әкеп соқпайтын зерттеулер жүргiзуге құқығы бар. Қазақстан Республикасының құқық қорғау немесе арнаулы мемлекеттік органы уәкілетті бөлімшесінің маманы қылмыстық процесті жүргізетін органның рұқсатымен сот-сараптамалық зерттеулердің барысы мен нәтижелерін осы Кодекстің 117-бабының талаптарына сәйкес ресімделген маманның қорытындысында көрсете отырып, осы объектілер бойынша оны жүргізуді жоққа шығармайтын көлемде осы объектiлердiң iшiнара жойылуына әкелетін салыстырмалы зерттеулер жүргiзуге;\n" +
                    "7) өзi қатысқан тергеу әрекетiнiң хаттамасымен, сондай-ақ тиiстi бөлiгiнде сот отырысының хаттамасымен танысуға және өзiнiң қатысуымен жүргiзiлген әрекеттердiң барысы мен нәтижелерiнiң тіркелуiнiң толықтығы мен дұрыстығына қатысты, хаттамаға енгiзілуге жататын мәлiмдемелер мен ескертулер жасауға;\n" +
                    "8) қылмыстық процестi жүргiзетін органның әрекеттерiне шағым келтіруге;\n" +
                    "9) аудармашының тегін көмегін пайдалануға;\n" +
                    "10) аудармашыға қарсылық білдіруді мәлімдеуге;\n" +
                    "11) қауіпсіздік шараларын қолдану туралы өтінішхат мәлімдеуге;\n" +
                    "12) егер iс бойынша iс жүргiзуге қатысуы оның лауазымдық мiндеттерiнiң шеңберiне кiрмейтiн болса, өзiнiң тергеу немесе сот әрекеттерiне қатысуына байланысты шеккен шығыстарына өтем және орындаған жұмысы үшiн сыйақы алуға құқығы бар.Маман:\n" +
                    "1) қылмыстық процесті жүргізетін органға хабардар етпестен, процеске қатысушылармен зерттеу жүргізуге байланысты мәселелер бойынша келіссөздер жүргізуге;\n" +
                    "2) зерттеу материалдарын өз бетінше жинауға құқылы емес.\n" +
                    "Маман:\n" +
                    "1) қылмыстық процесті жүргізетін органның шақыруы бойынша келуге;\n" +
                    "2) дәлелдемелердi жинауға, зерттеу мен бағалауға жәрдем көрсету үшiн арнаулы бiлiмiн, дағдысын және ғылыми-техникалық құралдарды пайдалана отырып, тергеу әрекеттерiн жүргiзу мен сот талқылауына қатысуға;\n" +
                    "3) өзi орындаған әрекеттерге қатысты түсiнiк беруге, ал осы баптың екінші бөлігінде көзделген жағдайда зерттеу жүргізуге және қорытынды беруге;\n" +
                    "4) iстiң мән-жайлары туралы мәлiметтердi және iске қатысуына байланысты өзiне белгiлi болған өзге де мәлiметтердi жария етпеуге;\n" +
                    "5) тергеу әрекеттерi жүргiзілген кезде және сот отырысы уақытында тәртiп сақтауға;\n" +
                    "6) зерттеуге ұсынылған объектілердің сақталуын қамтамасыз етуге мiндеттi.\n" +
                    "Өзiнiң мiндеттерiн орындаудан дәлелді себептерсіз бас тартқаны немесе жалтарғаны үшiн маманға осы Кодекстің 160-бабында белгіленген тәртіппен ақшалай өндіріп алу қолданылуы мүмкін.                 \n" +
                    "Көрінеу жалған қорытынды берген жағдайда маман заңда белгіленген қылмыстық жауаптылықта болады.\n";

    public static final String RIGHTS_ST79_EXPERT_KAZ =
            "Сарапшының:\n" +
                    "1) сараптаманың нысанасына жататын материалдармен (іс материалдарымен) танысуға;\n" +
                    "2) қорытынды беру үшін өзіне қажетті қосымша материалдарды беру туралы, сондай-ақ қауіпсіздік шараларын қолдану туралы өтінішхаттар мәлімдеуге;\n" +
                    "3) қылмыстық процесті жүргізетін органның рұқсатымен процестік әрекеттерді жүргізуге және сот отырысына қатысуға және оларға қатысатын тұлғаларға сараптама нысанасына қатысты сұрақтар қоюға;\n" +
                    "4) өзі қатысқан процестік әрекеттің хаттамасымен, сондай-ақ тиісті бөлігінде сот отырысының хаттамасымен танысуға және өзінің әрекеттері мен айғақтарының толық және дұрыс тіркелуіне қатысты, хаттамаларға енгізілуге жататын ескертулер жасауға;\n" +
                    "5) сараптаманы тағайындаған органның келісімі бойынша, сот-сараптамалық зерттеу барысында анықталған, іс үшін маңызы бар, сот сараптамасын тағайындау туралы қаулыда қамтылған мәселелердің шегінен шығатын мән-жайлар бойынша өз құзыреті шегінде қорытынды беруге;\n" +
                    "6) ана тілінде немесе өзі білетін тілде қорытынды ұсынуға және айғақтар беруге; аудармашының тегін көмегін пайдалануға; аудармашыға қарсылық білдіруді мәлімдеуге;\n" +
                    "7) қылмыстық процесті жүргізетін органның және іс бойынша іс жүргізуге қатысатын өзге де тұлғалардың сараптама жүргізу кезінде өзінің құқықтарына қысым көрсететін шешімдері мен әрекеттеріне шағым жасауға;\n" +
                    "8) сараптама жүргізу кезінде шегілген шығыстарға өтем және егер сот сараптамасын жүргізу өзінің лауазымдық міндеттерінің шеңберіне кірмейтін болса, орындалған жұмысы үшін сыйақы алуға құқығы бар.\n" +
                    "\n" +
                    "Сарапшы:\n" +
                    "1) қылмыстық процесті жүргізетін органды хабардар етпестен, процеске қатысушылармен сараптама жүргізуге байланысты мәселелер бойынша келіссөздер жүргізуге;\n" +
                    "2) зерттеу үшін материалдарды өз бетінше жинауға;\n" +
                    "3) егер сараптама тағайындаған органның бұған арнайы рұқсаты болмаса, объектілерді толық немесе ішінара жоюға не олардың сыртқы түрін немесе негізгі қасиеттерін өзгертуге әкеліп соғуы мүмкін зерттеулер жүргізуге құқылы емес.\n" +
                    "Сарапшы:\n" +
                    "1) қылмыстық процесті жүргізетін органның шақыруы бойынша келуге;\n" +
                    "2) өзіне ұсынылған объектілерге жан-жақты, толық және объективті зерттеу жүргізуге, қойылған мәселелер бойынша негізделген және объективті жазбаша қорытынды беруге;\n" +
                    "3) осы Кодекстің 284-бабында көзделген жағдайларда қорытынды беруден бас тартуға және қорытынды берудің мүмкін еместігі туралы уәжді жазбаша хабар жасауға және оны қылмыстық процесті жүргізетін органға жіберуге;\n" +
                    "4) жүргізілген зерттеуге және берілген қорытындыға байланысты мәселелер бойынша айғақтар беруге;\n" +
                    "5) зерттеуге ұсынылған объектілердің сақталуын қамтамасыз етуге;\n" +
                    "6) істің мән-жайы туралы мәліметтерді және сараптама жүргізуге байланысты өзіне белгілі болған өзге де мәліметтерді жария етпеуге;\n" +
                    "7) сараптаманы тағайындаған органға шығыстар сметасын және сараптама жүргізуге байланысты шегілген шығыстар туралы есепті ұсынуға міндетті.\n" +
                    "Сарапшы көрінеу жалған қорытынды бергені үшін заңда белгіленген қылмыстық жауаптылықта болады.\n" +
                    "Өзiнiң мiндеттерiн орындаудан дәлелді себептерсіз бас тартқаны немесе жалтарғаны үшiн сарапшыға осы Кодекстің 160-бабында белгіленген тәртіппен ақшалай өндіріп алу қолданылуы мүмкін. ";
}