package org.di.digital.service.export.kz;

import org.apache.poi.xwpf.usermodel.*;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.dto.response.CaseInterrogationProtocolResponse;
import org.di.digital.model.User;
import org.di.digital.service.export.BaseInterrogationDocBuilder;
import org.di.digital.util.LocalizationHelper;

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
        addJustifiedParagraph(doc, rightsText);
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc,
                "ҚР ҚПК-нің " + article + "-бабымен көзделген құқықтар мен міндеттер маған түсіндірілді. Құқықтардың мәні түсінікті.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);
    }

    // ── Закрывающие блоки ────────────────────────────────────────────────────

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
        famValue.setText(" танысты.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Жауап алу хаттамасы дұрыс жазылды және жауап алудың барлық барысын көрсетеді және "
                        + "жауап алу барысында берілген айғақтарды толық қамтиды.");
        addEmptyLine(doc);

        XWPFParagraph appPara = doc.createParagraph();
        appPara.setAlignment(ParagraphAlignment.BOTH);
        appPara.setIndentationFirstLine(720);
        XWPFRun appLabel = appPara.createRun();
        appLabel.setText("Хаттамаға енгізуге жататын өтініштер, ескертулер, толықтырулар, нақтылаулар мен түзетулер   ");
        appLabel.setFontFamily(FONT); appLabel.setFontSize(FONT_SIZE);
        XWPFRun appValue = appPara.createRun();
        appValue.setUnderline(UnderlinePatterns.SINGLE);
        appValue.setText(safe(data.getApplication()).equals("—") ? "жоқ" : data.getApplication());
        appValue.setFontFamily(FONT); appValue.setFontSize(FONT_SIZE);
        addEmptyLine(doc);

        addInlineSignatureLine(doc, roleLabel, fio);
        addEmptyLine(doc);

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
            "Куәнің құқықтары:\n"
                    + "1) өзін, жұбайын (зайыбын) немесе жақын туыстарын қудалауға әкеп соғатын айғақтар беруден бас тартуға; "
                    + "2) ана тілінде немесе білетін тілде айғақтар беруге; "
                    + "3) аудармашының тегін көмегін пайдалануға; "
                    + "4) аудармашыға қарсылық білдіруге; "
                    + "5) жауап алу хаттамасына айғақтарды өз қолымен жазуға; "
                    + "6) шағымдар келтіруге, өтініштер мәлімдеуге, қауіпсіздік шараларын қолдану туралы өтініштер беруге құқығы бар.\n\n"
                    + "Куәнің адвокатының қатысуымен айғақтар беруге құқығы бар.\n\n"
                    + "Куә міндеттері:\n"
                    + "1) шақыруы бойынша келуге; "
                    + "2) іс бойынша белгілі барлық мән-жайларды шынайы хабарлауға; "
                    + "3) мәліметтерді жария етпеуге; "
                    + "4) тәртіпті сақтауға міндетті.\n\n"
                    + "Жалған айғақтар бергені, айғақтар беруден бас тартқаны үшін ҚР ҚК-нің 420, 421-баптарымен "
                    + "көзделген қылмыстық жауапкершілік.\n\n"
                    + "Куәге қылмыстық құқық бұзушылық жасағаны үшін өзінің, жұбайының (зайыбының), "
                    + "жақын туыстарының қудалануына әкеп соғатын айғақтар беруден бас тартуға құқығы түсіндірілді.";

    public static final String RIGHTS_ST71_KAZ =
            "Зардап шеккеннің құқықтары:\n"
                    + "1) қойылған күдік пен айып туралы білуге; 2) ана тілінде немесе білетін тілде айғақтар беруге; "
                    + "3) дәлелдемелер ұсынуға; 4) өтініштер және қарсылықтар мәлімдеуге; "
                    + "5) аудармашының тегін көмегін пайдалануға; 6) өкілі болуға; "
                    + "7) алып қойылған мүлкін қайтарып алуға; 8) келісімге келуге; "
                    + "9) тергеу әрекеттерінің хаттамаларымен танысуға; "
                    + "10) тергеу әрекеттеріне қатысуға; 11) іс материалдарымен танысуға; "
                    + "12) қауіпсіздік шаралары туралы өтініштер беруге; "
                    + "13) қаулылардың көшірмелерін алуға; 14) сот талқылауына қатысуға; "
                    + "15) сот жарыссөзінде сөз сөйлеуге; 16) айыптауды қолдауға; "
                    + "17) хаттамаларға ескертулер беруге; 18) шағымдар келтіруге; "
                    + "19) үкімді және қаулыларды шағымдануға құқығы бар.\n\n"
                    + "Зардап шеккеннің міндеттері: шақыруы бойынша келуге, іс бойынша белгілі барлық мән-жайларды "
                    + "шынайы хабарлауға, мәліметтерді жария етпеуге, тәртіпті сақтауға міндетті.\n\n"
                    + "Жалған арыз жасағаны, жалған айғақтар бергені үшін қылмыстық жауапкершілік.\n\n"
                    + "Зардап шеккенге өзінің, жұбайының, жақын туыстарының қудалануына әкеп соғатын "
                    + "айғақтар беруден бас тартуға құқығы түсіндірілді.";

    public static final String RIGHTS_ST64_KAZ =
            "Күдіктінің құқықтары:\n"
                    + "1) құқықтар туралы түсіндірме алуға; 2) не үшін күдік келтірілгенін білуге; "
                    + "3) қорғаушы шақыруға; 4) қорғаушымен кездесуге; "
                    + "5) қорғаушының қатысуымен айғақтар беруге; "
                    + "6) қаулылардың көшірмелерін алуға; 7) айғақтар беруден бас тартуға; "
                    + "8) бұлтартпау шарасы туралы түсіндірме алуға; "
                    + "9) дәлелдемелер ұсынуға; 10) өтініштер мен қарсылықтар мәлімдеуге; "
                    + "11) ана тілінде немесе білетін тілде айғақтар беруге; "
                    + "12) аудармашының тегін көмегін пайдалануға; "
                    + "13) тергеу әрекеттеріне қатысуға; 14) зардап шеккенмен келісімге келуге; "
                    + "15) процестік келісім жасау туралы өтініш беруге; "
                    + "16) хаттамаларға ескертулер беруге; 17) шағымдар келтіруге; "
                    + "18) құқықтарын қорғауға; 19) сараптама тағайындау кезінде іс-әрекеттер жасауға; "
                    + "20) іс материалдарымен танысуға; 21) қылмыстық қудалауды тоқтатуға қарсылық білдіруге құқығы бар.\n\n"
                    + "Күдікті бірінші жауап алу басталғанға дейін айғақтар беруден бас тартуға құқығы бар.";

    public static final String RIGHTS_ST65_1_KAZ =
            "Қорғалуға құқығы бар куәнің құқықтары:\n"
                    + "1) өзіне тиесілі құқықтар туралы түсіндірме алуға; "
                    + "2) қорғалуға құқығы бар куә мәртебесі туралы түсіндірме алуға; "
                    + "3) сараптама тағайындау туралы қаулымен танысуға; "
                    + "4) сараптаманың қорытындысымен танысуға; "
                    + "5) айғақтар беруден бас тартуға; "
                    + "6) қорғаушы шақыруға; "
                    + "7) қорғаушының қатысуымен айғақтар беруге; "
                    + "8) ана тілінде немесе білетін тілде айғақтар беруге; "
                    + "9) аудармашының тегін көмегін пайдалануға; "
                    + "10) жауап алу хаттамасына айғақтарды өз қолымен жазуға; "
                    + "11) құжаттармен танысуға; "
                    + "12) тергеу әрекеттерінің хаттамаларымен танысуға және оларға ескертулер беруге; "
                    + "13) өтініштер мәлімдеуге; "
                    + "14) қарсылықтар білдіруге; "
                    + "15) өзіне қарсы айғақтар берушілермен бетпе-бет кездесуге құқығы бар.\n\n"
                    + "Қорғалуға құқығы бар куә міндеттері: сотқа дейінгі тергеп-тексеруді жүзеге асыратын адамның "
                    + "шақыруы бойынша келуге; тәртіпті сақтауға міндетті.";

    public static final String RIGHTS_ST80_SPECIALIST_KAZ =
            "Маманның құқықтары:\n"
                    + "1) зерттеу пәніне қатысты материалдармен танысуға; "
                    + "2) қосымша материалдар беру туралы өтініштер мәлімдеуге; "
                    + "3) шақыру мақсатын білуге; "
                    + "4) тиісті арнайы білімі болмаса, іс бойынша іс жүргізуге қатысудан бас тартуға; "
                    + "5) тергеу әрекетіне қатысушыларға сұрақтар қоюға; "
                    + "6) іс материалдарын зерттеуді жүргізуге; "
                    + "7) тергеу әрекетінің хаттамасымен танысуға және ескертулер жасауға; "
                    + "8) шағымдар келтіруге; "
                    + "9) аудармашының тегін көмегін пайдалануға; "
                    + "10) аудармашыға қарсылық білдіруге; "
                    + "11) қауіпсіздік шараларын қолдану туралы өтініш беруге; "
                    + "12) шығындарды өтеуді және орындалған жұмысы үшін сыйақы алуға құқығы бар.\n\n"
                    + "Маман міндеттері: қылмыстық процесті жүргізетін органның шақыруы бойынша келуге; "
                    + "арнайы білімдерін пайдаланып тергеу әрекеттерін жүргізуге қатысуға; "
                    + "орындалатын әрекеттер туралы түсіндірулер беруге; тәртіпті сақтауға міндетті.";

    public static final String RIGHTS_ST82_EXPERT_KAZ =
            "Сарапшының құқықтары:\n"
                    + "1) сараптама пәніне қатысты іс материалдарымен танысуға; "
                    + "2) қосымша материалдар беру туралы өтініштер мәлімдеуге; "
                    + "3) тергеу және сот әрекеттеріне қатысуға; "
                    + "4) тергеу әрекетінің хаттамасымен танысуға және ескертулер жасауға; "
                    + "5) қорытындыда іс үшін маңызды мән-жайларды көрсетуге; "
                    + "6) шағымдар келтіруге; "
                    + "7) аудармашының тегін көмегін пайдалануға; "
                    + "8) аудармашыға қарсылық білдіруге; "
                    + "9) қауіпсіздік шараларын қолдану туралы өтініш беруге; "
                    + "10) шығындарды өтеуді және сыйақы алуға құқығы бар.\n\n"
                    + "Сарапшы міндеттері: қылмыстық процесті жүргізетін органның шақыруы бойынша келуге; "
                    + "толық зерттеу жүргізуге және жазбаша қорытынды беруге; "
                    + "мән-жайларды жария етпеуге; тәртіпті сақтауға міндетті.\n\n"
                    + "Жалған қорытынды бергені үшін сарапшы қылмыстық жауапкершілік көтереді.";
}