package org.di.digital.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.*;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.dto.response.CaseInterrogationProtocolResponse;
import org.di.digital.dto.response.CaseInterrogationQAResponse;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InterrogationExportService {

    private static final DateTimeFormatter DATE_RU =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'года'", new Locale("ru"));
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH 'час.' mm 'мин.'");

    public byte[] exportToDocx(CaseInterrogationFullResponse data) {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            setPageMargins(doc);

            String role = data.getRole() != null ? data.getRole().trim() : "Свидетель";
            String fio = safe(data.getFio());
            String caseNumber = safe(data.getCaseNumber());

            // 1. Заголовок
            addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
            addCenteredParagraph(doc, getRoleTitle(role), 12);
            addEmptyLine(doc);

            // 2. Город + дата | время
            addCityDateBlock(doc, data);
            addEmptyLine(doc);

            // 3. Вводный абзац (следователь, дело, в качестве кого)
            addIntroBlock(doc, data, role, caseNumber);
            addEmptyLine(doc);

            // 4. Анкетные данные — таблица
            addPersonDataTable(doc, data);
            addEmptyLine(doc);

            // 5. Уведомление о допросе по делу
            addJustifiedParagraph(doc,
                    getRoleLabel(role) + "у(ей) " + fio + " сообщено, что он(а) будет допрошен(а) " +
                            "по уголовному делу №" + caseNumber + ".");
            addEmptyLine(doc);

            // 6. Права — полный текст по роли
            addRightsSection(doc, role, fio);

            // 7. Показания — вопросы и ответы
            addTestimonyIntro(doc, role, fio);
            addQABlock(doc, data, role, fio);

            // 8. Закрытие
            addClosingSection(doc, role, fio);

            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при генерации DOCX", e);
        }
    }

    // ─── Блоки ────────────────────────────────────────────────────────────────

    private void addCityDateBlock(XWPFDocument doc, CaseInterrogationFullResponse data) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);

        XWPFRun cityRun = p.createRun();
        cityRun.setText("г.__________");
        cityRun.setFontFamily("Times New Roman");
        cityRun.setFontSize(12);

        // Большой отступ до даты
        XWPFRun tabRun = p.createRun();
        tabRun.setText("          ");
        tabRun.setFontFamily("Times New Roman");
        tabRun.setFontSize(12);

        XWPFRun dateRun = p.createRun();
        String dateStr = data.getDate() != null ? data.getDate().format(DATE_RU) : "«___» ________ ____ года";
        dateRun.setText(dateStr);
        dateRun.setFontFamily("Times New Roman");
        dateRun.setFontSize(12);

        addEmptyLine(doc);

        // Время начат / окончен — по правому краю
        XWPFParagraph timePara = doc.createParagraph();
        timePara.setAlignment(ParagraphAlignment.RIGHT);

        String startStr = data.getStartedAt() != null
                ? data.getStartedAt().format(TIME_FMT) : "__ час. __ мин.";
        String endStr = data.getFinishedAt() != null
                ? data.getFinishedAt().format(TIME_FMT) : "__ час. __ мин.";

        XWPFRun startRun = timePara.createRun();
        startRun.setBold(true);
        startRun.setText("Допрос начат: " + startStr);
        startRun.setFontFamily("Times New Roman");
        startRun.setFontSize(12);
        startRun.addBreak();

        XWPFRun endRun = timePara.createRun();
        endRun.setBold(true);
        endRun.setText("Допрос окончен: " + endStr);
        endRun.setFontFamily("Times New Roman");
        endRun.setFontSize(12);
    }

    private void addIntroBlock(XWPFDocument doc, CaseInterrogationFullResponse data,
                               String role, String caseNumber) {
        String roleGenitive = getRoleGenitive(role);
        String text =
                "Следователь ___________________________ в помещении кабинета №___ " +
                        "_______________________________________________, " +
                        "расположенного по адресу: ___________________________ " +
                        "с соблюдением требований ст.ст." + getRightsArticles(role) + " УПК РК " +
                        "допросил по уголовному делу №" + caseNumber + " " +
                        "в качестве " + roleGenitive + "(ей):";
        addJustifiedParagraph(doc, text);
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
                {"Контактные телефоны:", p != null ? safe(p.getContacts()) : "—"},
                {"Электронная почта:", "—"},
                {"Отношение к воинской обязанности:", p != null ? safe(p.getMilitary()) : "—"},
                {"Наличие судимости:", p != null ? safe(p.getCriminalRecord()) : "—"},
                {"Паспорт или иной документ, удостоверяющий личность:",
                        "ИИН " + safe(data.getIin()) +
                                (p != null && p.getIinOrPassport() != null ? "\n" + p.getIinOrPassport() : "")},
                {"Иные данные о личности:", "—"},
        };

        XWPFTable table = doc.createTable(rows.length, 2);
        removeTableBorders(table);

        for (int i = 0; i < rows.length; i++) {
            XWPFTableRow row = table.getRow(i);

            XWPFTableCell labelCell = row.getCell(0);
            setCellWidth(labelCell, 3600);
            setCellThinBorder(labelCell);
            setCellPadding(labelCell);
            XWPFRun lr = labelCell.getParagraphs().get(0).createRun();
            lr.setText(rows[i][0]);
            lr.setFontFamily("Times New Roman");
            lr.setFontSize(11);

            XWPFTableCell valueCell = row.getCell(1);
            setCellWidth(valueCell, 5726);
            setCellThinBorder(valueCell);
            setCellPadding(valueCell);
            XWPFRun vr = valueCell.getParagraphs().get(0).createRun();
            vr.setText(rows[i][1]);
            vr.setFontFamily("Times New Roman");
            vr.setFontSize(11);
        }
    }

    private void addRightsSection(XWPFDocument doc, String role, String fio) {
        // Вводная строка
        addJustifiedParagraph(doc,
                getRoleLabel(role) + "у(ей) " + fio +
                        " разъяснены права и обязанности, предусмотренные " +
                        getRightsArticle(role) + " УПК РК, а именно:");
        addEmptyLine(doc);

        // Текст прав
        addJustifiedParagraph(doc, getFullRightsText(role));
        addEmptyLine(doc);

        // Подтверждение прочтения
        addJustifiedItalicParagraph(doc,
                "Права и обязанности, предусмотренные " + getRightsArticle(role) +
                        " УПК РК, мне разъяснены. Сущность прав ясна.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, getRoleLabel(role), fio);
        addEmptyLine(doc);

        // Язык показаний
        addJustifiedItalicParagraph(doc,
                "Я, " + fio + ", показания желаю давать на русском языке, " +
                        "в помощи переводчика не нуждаюсь.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, getRoleLabel(role), fio);
        addEmptyLine(doc);

        // Предупреждение об ответственности
        addJustifiedParagraph(doc, getResponsibilityWarning(role, fio));
        addEmptyLine(doc);
        addInlineSignatureLine(doc, getRoleLabel(role), fio);
        addEmptyLine(doc);
    }

    private void addTestimonyIntro(XWPFDocument doc, String role, String fio) {
        addJustifiedParagraph(doc,
                getRoleLabel(role) + "у(ей) предложено рассказать об известных обстоятельствах, " +
                        "имеющих значение для дела, на что " + getRoleLabel(role).toLowerCase() +
                        " " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);

        // Заявление о языке показаний — курсив
        addJustifiedItalicParagraph(doc,
                "Я, " + fio + ", показания желаю давать на русском языке, " +
                        "в помощи переводчика не нуждаюсь.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, getRoleLabel(role), fio);
        addEmptyLine(doc);
    }

    private void addQABlock(XWPFDocument doc, CaseInterrogationFullResponse data,
                            String role, String fio) {
        if (data.getQaList() == null || data.getQaList().isEmpty()) return;

        for (CaseInterrogationQAResponse qa : data.getQaList()) {
            // Вопрос — жирный
            XWPFParagraph qPara = doc.createParagraph();
            qPara.setAlignment(ParagraphAlignment.BOTH);
            qPara.setIndentationFirstLine(720);
            XWPFRun qRun = qPara.createRun();
            qRun.setBold(true);
            qRun.setText("Вопрос следователя: " + safe(qa.getQuestion()));
            qRun.setFontFamily("Times New Roman");
            qRun.setFontSize(12);

            // Ответ — обычный с отступом
            XWPFParagraph aPara = doc.createParagraph();
            aPara.setAlignment(ParagraphAlignment.BOTH);
            aPara.setIndentationFirstLine(720);
            XWPFRun aRun = aPara.createRun();
            String answer = (qa.getAnswer() != null && !qa.getAnswer().isBlank())
                    ? qa.getAnswer() : "Ответ не получен.";
            aRun.setText("Ответ: " + answer);
            aRun.setFontFamily("Times New Roman");
            aRun.setFontSize(12);

            addEmptyLine(doc);
            addInlineSignatureLine(doc, getRoleLabel(role), fio);
            addEmptyLine(doc);
        }
    }

    private void addClosingSection(XWPFDocument doc, String role, String fio) {
        addJustifiedParagraph(doc,
                "На этом допрос " + getRoleGenitive(role) + " окончен. " +
                        "Участникам допроса разъяснено право ознакомиться с результатами допроса, " +
                        "а также право внесения в протокол заявлений, замечаний, дополнений, " +
                        "уточнений и исправлений.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, getRoleLabel(role), fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Участникам следственного действия протокол допроса " +
                        getRoleGenitive(role) + " предъявлен для ознакомления. " +
                        "С настоящим протоколом допроса ознакомлен(ы) путем личного прочтения. " +
                        "Заявлений, замечаний, дополнений, уточнений и исправлений, " +
                        "подлежащих внесению в протокол не имею(ем).");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, getRoleLabel(role), fio);
        addEmptyLine(doc);
        addEmptyLine(doc);

        // Блок следователя
        XWPFParagraph investigatorLabel = doc.createParagraph();
        XWPFRun ilRun = investigatorLabel.createRun();
        ilRun.setBold(true);
        ilRun.setText("Допросил, протокол составил:");
        ilRun.setFontFamily("Times New Roman");
        ilRun.setFontSize(12);

        addEmptyLine(doc);

        XWPFParagraph investigatorSig = doc.createParagraph();
        XWPFRun isRun = investigatorSig.createRun();
        isRun.setText("___________________________                    ___________________________");
        isRun.setFontFamily("Times New Roman");
        isRun.setFontSize(12);

        XWPFParagraph investigatorNote = doc.createParagraph();
        investigatorNote.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun inRun = investigatorNote.createRun();
        inRun.setText("(должность, звание, ФИО)                       (подпись)");
        inRun.setFontFamily("Times New Roman");
        inRun.setFontSize(10);
        inRun.setColor("888888");
    }

    // ─── Тексты прав ──────────────────────────────────────────────────────────

    private String getFullRightsText(String role) {
        return switch (role.toLowerCase()) {
            case "потерпевший", "потерпевшая" -> RIGHTS_ST71;
            case "подозреваемый", "подозреваемая" -> RIGHTS_ST64;
            default -> RIGHTS_ST78;
        };
    }

    private String getResponsibilityWarning(String role, String fio) {
        return switch (role.toLowerCase()) {
            case "подозреваемый", "подозреваемая" ->
                    getRoleLabel(role) + " " + fio +
                            " предупрежден(а) о том, что его(её) показания могут быть использованы " +
                            "в качестве доказательств в уголовном процессе, если он(а) не воспользовался(ась) " +
                            "своим правом отказаться от дачи показаний до начала первого допроса, " +
                            "в том числе и при его(её) последующем отказе от этих показаний.";
            default ->
                    getRoleLabel(role) + " " + fio +
                            " предупрежден(а) об уголовной ответственности за заведомо ложные показания " +
                            "и за отказ от дачи показаний, в суде либо в ходе досудебного расследования, " +
                            "предусмотренными ст.ст.420, 421 УК РК.";
        };
    }

    private static final String RIGHTS_ST71 =
            "Потерпевший имеет право:\n" +
                    "1) знать о предъявленном подозрении и обвинении; 2) давать показания на родном языке " +
                    "или языке, которым владеет; 3) представлять доказательства; 4) заявлять ходатайства " +
                    "и отводы; 5) пользоваться бесплатной помощью переводчика; 6) иметь представителя; " +
                    "7) получать имущество, изъятое у него органом уголовного преследования в качестве " +
                    "средства доказывания или представленное им самим, а также принадлежащее ему имущество, " +
                    "изъятое у лица, совершившего запрещенное уголовным законом деяние, получать " +
                    "принадлежащие ему подлинники документов; 8) примириться, в том числе в порядке медиации, " +
                    "с подозреваемым, обвиняемым, подсудимым в случаях, предусмотренных законом; " +
                    "8-1) выразить согласие на применение приказного производства по делу об уголовном " +
                    "проступке или о преступлении небольшой тяжести; 9) знакомиться с протоколами " +
                    "следственных действий, производимых с его участием, и подавать на них замечания; " +
                    "10) участвовать с разрешения следователя или дознавателя в следственных действиях, " +
                    "проводимых по его ходатайству либо ходатайству его представителя; 11) знакомиться " +
                    "по окончании досудебного расследования со всеми материалами дела, выписывать из него " +
                    "любые сведения и в любом объеме, за исключением сведений, составляющих государственные " +
                    "секреты; 12) заявлять ходатайства о предоставлении мер безопасности ему и членам его " +
                    "семьи, неразглашении обстоятельств частной жизни, о применении в отношении " +
                    "подозреваемого запрета на приближение; 13) получить копии постановлений о признании " +
                    "его потерпевшим или отказе в этом, прекращении досудебного расследования, " +
                    "обвинительного акта, а также копии приговора и постановления суда первой, " +
                    "апелляционной и кассационной инстанций; 14) участвовать в судебном разбирательстве " +
                    "дела в суде первой, апелляционной и кассационной инстанций; 15) выступать в судебных " +
                    "прениях; 16) поддерживать обвинение, в том числе и в случае отказа государственного " +
                    "обвинителя от обвинения; 17) знакомиться с протоколом судебного заседания и подавать " +
                    "замечания на протокол; 18) приносить жалобы на действия (бездействие) органа, " +
                    "ведущего уголовный процесс; 19) обжаловать приговор и постановления суда; " +
                    "20) знать о принесенных по делу жалобах, ходатайствах прокурора и протестах, " +
                    "подавать на них возражения и участвовать в их рассмотрении; 21) защищать свои права " +
                    "и законные интересы иными способами, не противоречащими закону; 22) знать о намерении " +
                    "сторон заключить процессуальное соглашение, о его условиях и последствиях, предлагать " +
                    "свои условия по возмещению ущерба, причиненного преступлением, либо возражать против " +
                    "его заключения; 23) на получение компенсации в соответствии с законодательством " +
                    "Республики Казахстан о Фонде компенсации потерпевшим.\n\n" +
                    "Потерпевший обязан: явиться по вызову органа, ведущего уголовный процесс, правдиво " +
                    "сообщить все известные по делу обстоятельства и ответить на поставленные вопросы; " +
                    "не разглашать сведения об обстоятельствах, известных ему по делу; соблюдать " +
                    "установленный порядок при производстве следственных действий и во время судебного заседания.\n\n" +
                    "При неявке потерпевшего по вызову без уважительных причин он может быть подвергнут " +
                    "принудительному приводу в порядке, предусмотренном статьей 157 УПК, и на него может " +
                    "быть наложено денежное взыскание в порядке, предусмотренном статьей 160 УПК.\n\n" +
                    "За отказ от дачи показаний и дачу заведомо ложных показаний потерпевший несет " +
                    "в соответствии с законом уголовную ответственность.\n\n" +
                    "Потерпевшему(ей) разъяснено право отказаться от дачи показаний, уличающих в " +
                    "совершении уголовного правонарушения его самого, супруга (супруги), близких родственников.";

    private static final String RIGHTS_ST78 =
            "Свидетель имеет право:\n" +
                    "1) отказаться от дачи показаний, которые могут повлечь для него самого, его супруга " +
                    "(супруги) или близких родственников преследование за совершение уголовно наказуемого " +
                    "деяния или административного правонарушения; 2) давать показания на своем родном языке " +
                    "или языке, которым владеет; 3) пользоваться бесплатной помощью переводчика; " +
                    "4) заявлять отвод переводчику, участвующему в его допросе; 5) собственноручной записи " +
                    "показаний в протоколе допроса; 6) приносить жалобы на действия дознавателя, " +
                    "следователя, прокурора и суда, заявлять ходатайства, касающиеся его прав и законных " +
                    "интересов, в том числе о принятии мер безопасности.\n\n" +
                    "Свидетель имеет право давать показания в присутствии своего адвоката.\n\n" +
                    "Свидетелю обеспечивается возмещение расходов, понесенных им при производстве " +
                    "по уголовному делу.\n\n" +
                    "Свидетель обязан:\n" +
                    "1) явиться по вызову дознавателя, следователя, прокурора и суда; 2) правдиво сообщить " +
                    "все известное по делу и ответить на поставленные вопросы; 3) не разглашать сведения " +
                    "об обстоятельствах, известных ему по делу, если он был предупрежден об этом " +
                    "дознавателем, следователем или прокурором; 4) соблюдать установленный порядок при " +
                    "производстве следственных действий и во время судебного заседания.\n\n" +
                    "За дачу ложных показаний, отказ от дачи показаний свидетель несет уголовную " +
                    "ответственность, предусмотренную ст.ст.420, 421 УК РК.\n\n" +
                    "Свидетелю разъяснено право отказаться от дачи показаний, уличающих в совершении " +
                    "уголовного правонарушения его самого, супруга (супруги), близких родственников.";

    private static final String RIGHTS_ST64 =
            "Подозреваемый имеет право:\n" +
                    "1) получить от лица, осуществившего задержание, разъяснение принадлежащих мне прав; " +
                    "2) знать, в чем я подозреваюсь; 3) самостоятельно или через своих родственников или " +
                    "доверенных лиц пригласить защитника. В случае, если защитник не приглашен мною, " +
                    "моими родственниками или доверенными лицами, орган уголовного преследования обязан " +
                    "обеспечить его участие в порядке, предусмотренном частью третьей статьи 67 УПК РК; " +
                    "4) пользоваться правами гражданского ответчика в случае признания меня таковым в связи " +
                    "с предъявлением по делу гражданского иска; 5) иметь свидание с избранным или " +
                    "назначенным защитником наедине и конфиденциально, в том числе до начала допроса; " +
                    "6) давать показания только в присутствии защитника, за исключением случаев отказа от него; " +
                    "7) получить копии постановлений о признании подозреваемым, гражданским ответчиком, " +
                    "квалификации деяния, протокола задержания, ходатайства и постановления об избрании и " +
                    "продлении срока меры пресечения, постановления о прекращении уголовного дела; " +
                    "8) отказаться от дачи показаний; 9) получить от лица, осуществляющего досудебное " +
                    "расследование, разъяснение о порядке и условиях применения меры пресечения в виде " +
                    "залога и других мер, не связанных с содержанием под стражей; 10) представлять " +
                    "доказательства; 11) заявлять ходатайства, в том числе о принятии мер безопасности, " +
                    "и отводы; 12) давать показания на родном языке или языке, которым владеет; " +
                    "13) пользоваться бесплатной помощью переводчика; 14) участвовать с разрешения органа " +
                    "уголовного преследования в следственных действиях, проводимых по моему ходатайству " +
                    "или ходатайству моего защитника либо законного представителя; 15) примириться с " +
                    "потерпевшим в случаях, предусмотренных законом, в том числе в порядке медиации; " +
                    "16) на любой стадии расследования заявить ходатайство прокурору либо выразить ему " +
                    "согласие о заключении процессуального соглашения с изложением своих предложений о " +
                    "виде и мере наказания и заключить процессуальное соглашение; " +
                    "16-1) заявить ходатайство о применении приказного производства по делу об уголовном " +
                    "проступке или о преступлении небольшой тяжести; 17) знакомиться с протоколами " +
                    "следственных действий, произведенных с моим участием, и подавать замечания на протоколы; " +
                    "18) приносить жалобы на действия (бездействие) и решения следователя, дознавателя, " +
                    "прокурора и суда; 19) защищать свои права и законные интересы иными способами, не " +
                    "противоречащими закону; 20) при назначении и производстве экспертизы, а также " +
                    "предъявлении мне заключения эксперта осуществлять действия, предусмотренные " +
                    "статьями 274, 286 УПК РК; 21) в порядке, установленном Уголовно-процессуальным " +
                    "кодексом, знакомиться по окончании расследования с материалами дела и выписывать из " +
                    "него любые сведения, а также снимать копии с использованием научно-технических средств, " +
                    "за исключением сведений, составляющих государственные секреты или иную охраняемую " +
                    "законом тайну; 22) возражать против прекращения уголовного преследования; " +
                    "23) безотлагательно уведомляться органом, ведущим уголовный процесс, о принятии " +
                    "процессуальных решений, затрагивающих мои права и законные интересы; " +
                    "24) ходатайствовать о дополнительном допросе показывающего против меня свидетеля, " +
                    "вызове и допросе в качестве свидетелей указанных мной лиц на очную ставку с ними.\n\n" +
                    "Подозреваемый имеет право отказаться от дачи показаний до начала первого допроса.";

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String getRoleTitle(String role) {
        return switch (role.toLowerCase()) {
            case "потерпевший", "потерпевшая" -> "допроса потерпевшего";
            case "подозреваемый", "подозреваемая" -> "допроса подозреваемого";
            default -> "допроса свидетеля";
        };
    }

    private String getRoleLabel(String role) {
        return switch (role.toLowerCase()) {
            case "потерпевший", "потерпевшая" -> "Потерпевший(ая)";
            case "подозреваемый", "подозреваемая" -> "Подозреваемый(ая)";
            default -> "Свидетель";
        };
    }

    private String getRoleGenitive(String role) {
        return switch (role.toLowerCase()) {
            case "потерпевший", "потерпевшая" -> "потерпевшего";
            case "подозреваемый", "подозреваемая" -> "подозреваемого";
            default -> "свидетеля";
        };
    }

    private String getRightsArticle(String role) {
        return switch (role.toLowerCase()) {
            case "потерпевший", "потерпевшая" -> "ст. 71";
            case "подозреваемый", "подозреваемая" -> "ст. 64";
            default -> "ст. 78";
        };
    }

    private String getRightsArticles(String role) {
        return switch (role.toLowerCase()) {
            case "потерпевший", "потерпевшая" -> "71, 80, 81, 110, 115, 197, 199, 208–210, 212, 214";
            case "подозреваемый", "подозреваемая" -> "64, 80, 81, 110, 114 ч.5, 115, 197, 199, 208–210, 212, 216";
            default -> "60, 78, 110, 115, 197, 199 ч.3, 208, 209, 210, 212, 214";
        };
    }

    private void addCenteredBoldParagraph(XWPFDocument doc, String text, int fontSize) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setText(text);
        r.setFontFamily("Times New Roman");
        r.setFontSize(fontSize);
    }

    private void addCenteredParagraph(XWPFDocument doc, String text, int fontSize) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontFamily("Times New Roman");
        r.setFontSize(fontSize);
    }

    private void addJustifiedParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        p.setIndentationFirstLine(720);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontFamily("Times New Roman");
        r.setFontSize(12);
    }

    private void addJustifiedItalicParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        p.setIndentationFirstLine(720);
        XWPFRun r = p.createRun();
        r.setItalic(true);
        r.setText(text);
        r.setFontFamily("Times New Roman");
        r.setFontSize(12);
    }

    private void addInlineSignatureLine(XWPFDocument doc, String roleLabel, String fio) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r1 = p.createRun();
        r1.setText(roleLabel + ":   ");
        r1.setFontFamily("Times New Roman");
        r1.setFontSize(12);
        XWPFRun r2 = p.createRun();
        r2.setText("____________________          " + fio);
        r2.setFontFamily("Times New Roman");
        r2.setFontSize(12);
    }

    private void addEmptyLine(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setFontSize(6);
        r.setText("");
    }

    private void setPageMargins(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().addNewSectPr();
        CTPageMar pageMar = sectPr.addNewPgMar();
        pageMar.setTop(BigInteger.valueOf(1440));    // 1"
        pageMar.setBottom(BigInteger.valueOf(1440)); // 1"
        pageMar.setLeft(BigInteger.valueOf(1800));   // 1.25" — поля как в образцах
        pageMar.setRight(BigInteger.valueOf(1080));  // 0.75"
    }

    private void setCellWidth(XWPFTableCell cell, int widthDxa) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr()
                ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTblWidth w = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
        w.setW(BigInteger.valueOf(widthDxa));
        w.setType(STTblWidth.DXA);
    }

    private void setCellThinBorder(XWPFTableCell cell) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr()
                ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcBorders b = tcPr.isSetTcBorders()
                ? tcPr.getTcBorders() : tcPr.addNewTcBorders();
        for (CTBorder border : new CTBorder[]{
                b.addNewTop(), b.addNewBottom(), b.addNewLeft(), b.addNewRight()
        }) {
            border.setVal(STBorder.SINGLE);
            border.setSz(BigInteger.valueOf(2));
            border.setColor("000000");
        }
    }

    private void setCellPadding(XWPFTableCell cell) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr()
                ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcMar mar = tcPr.isSetTcMar() ? tcPr.getTcMar() : tcPr.addNewTcMar();
        for (CTTblWidth w : new CTTblWidth[]{
                mar.isSetTop() ? mar.getTop() : mar.addNewTop(),
                mar.isSetBottom() ? mar.getBottom() : mar.addNewBottom(),
                mar.isSetLeft() ? mar.getLeft() : mar.addNewLeft(),
                mar.isSetRight() ? mar.getRight() : mar.addNewRight()
        }) {
            w.setW(BigInteger.valueOf(80));
            w.setType(STTblWidth.DXA);
        }
    }

    private void removeTableBorders(XWPFTable table) {
        CTTbl ctTbl = table.getCTTbl();
        CTTblPr tblPr = ctTbl.getTblPr() != null ? ctTbl.getTblPr() : ctTbl.addNewTblPr();
        CTTblBorders borders = tblPr.getTblBorders() != null
                ? tblPr.getTblBorders() : tblPr.addNewTblBorders();
        for (CTBorder b : new CTBorder[]{
                borders.isSetTop() ? borders.getTop() : borders.addNewTop(),
                borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom(),
                borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft(),
                borders.isSetRight() ? borders.getRight() : borders.addNewRight(),
                borders.isSetInsideH() ? borders.getInsideH() : borders.addNewInsideH(),
                borders.isSetInsideV() ? borders.getInsideV() : borders.addNewInsideV()
        }) {
            b.setVal(STBorder.NONE);
        }
    }

    private String safe(String val) {
        return (val != null && !val.isBlank()) ? val : "—";
    }
}