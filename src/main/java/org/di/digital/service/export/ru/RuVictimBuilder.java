package org.di.digital.service.export.ru;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.model.User;
import org.di.digital.service.export.InterrogationProtocolBuilder;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Component;

import static org.di.digital.util.UserUtil.getCurrentUser;

/**
 * Протокол допроса потерпевшего (обычный и дополнительный).
 * role="victim", isDop=false/true
 */
@Component("ruVictimBuilder")
public class RuVictimBuilder extends RuBaseBuilder implements InterrogationProtocolBuilder {

    public RuVictimBuilder(LocalizationHelper localizationHelper) {
        super(localizationHelper);
    }

    @Override
    public void build(XWPFDocument doc, CaseInterrogationFullResponse data) {
        if (Boolean.TRUE.equals(data.getIsDop())) {
            buildDop(doc, data);
        } else {
            buildRegular(doc, data);
        }
    }

    private void buildRegular(XWPFDocument doc, CaseInterrogationFullResponse data) {
        User user = getCurrentUser();
        String fio = formatFio(data.getFio());

        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "допроса потерпевшего", 12);
        addEmptyLine(doc);
        addCityDateBlock(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLine(user, data)
                        + ", с соблюдением требований ст.ст.71, 80, 81, 110, 115, 197, 199, 208–210, 212, 214 УПК РК"
                        + " допросил по уголовному делу №" + safe(data.getCaseNumber())
                        + " в качестве потерпевшего:");
        addEmptyLine(doc);
        addPersonDataTable(doc, data);
        addEmptyLine(doc);

        addInvolvedBlock(doc, data);
        addJustifiedParagraph(doc, "Перед началом следственного действия участникам объявлен порядок производства допроса.");
        addEmptyLine(doc);

        addRightsSection(doc, "Пострадавшему", fio, "ст.71", RIGHTS_ST71);

        addJustifiedParagraph(doc,
                "Потерпевший(ая) " + fio
                        + " предупрежден(а): об уголовной ответственности по ст.419 УК РК за заведомо ложный донос, "
                        + "об уголовной ответственности по ст.420 УК РК за дачу заведомо ложных показаний; "
                        + "об уголовной ответственности по ст.421 УК РК за отказ или уклонение от дачи показаний.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Потерпевший(ая)", fio);
        addEmptyLine(doc);

        // Вводная фраза потерпевшего
        String language = safe(data.getLanguage()).equals("—") ? "русском" : data.getLanguage();
        addJustifiedItalicParagraph(doc, "После чего, потерпевший(ая) " + fio + " заявил(а):");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc,
                "Права потерпевшего, предусмотренные ст.71 УПК РК, мне разъяснены, от дачи показаний я не отказываюсь, "
                        + "желаю давать показания на " + language + " языке, которым владею свободно, "
                        + "в услугах переводчика не нуждаюсь, и это не связано с моим материальным положением, "
                        + "в мерах по обеспечению безопасности не нуждаюсь.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Потерпевший(ая)", fio);
        addEmptyLine(doc);
        addJustifiedParagraph(doc, "По существу заданных вопросов могу показать следующее:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Потерпевший(ая)", fio,
                "Вопрос следователя", "Ответ", "Ответ не получен.");
        addClosingSection(doc, data, "потерпевшего", "Потерпевший(ая)", fio);
    }

    private void buildDop(XWPFDocument doc, CaseInterrogationFullResponse data) {
        User user = getCurrentUser();
        String fio = formatFio(data.getFio());

        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "дополнительного допроса потерпевшего", 12);
        addEmptyLine(doc);
        addCityDateBlock(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLine(user, data)
                        + ", с соблюдением требований ст.ст.71, 110, 115, 197, 199, 209-212, 214 УПК РК"
                        + " дополнительно допросил в качестве потерпевшего"
                        + " по материалам досудебного расследования №" + safe(data.getCaseNumber()) + ":");
        addEmptyLine(doc);
        addPersonDataTableVictimDop(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Перед началом допроса в соответствии со ст.ст.110, 214 УПК РК потерпевшему разъяснены "
                        + "процессуальные права и обязанности потерпевшего, в том числе права не свидетельствовать "
                        + "против самого себя, супруга (супруги) и своих близких родственников, круг которых определен законом. "
                        + "Одновременно разъяснено, что в соответствии со ст.71 УПК РК:");
        addEmptyLine(doc);
        addJustifiedParagraph(doc, RIGHTS_ST71_FULL);
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Потерпевший(ая)", fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Потерпевший(ая) " + fio
                        + " предупрежден(а): об уголовной ответственности по ст.419 УК РК за заведомо ложный донос, "
                        + "по ст.420 УК РК за дачу заведомо ложных показаний; по ст.421 УК РК за отказ или уклонение от дачи показаний.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Потерпевший(ая)", fio);
        addEmptyLine(doc);

        String language = safe(data.getLanguage()).equals("—") ? "русском" : data.getLanguage();
        addJustifiedItalicParagraph(doc, "После чего, потерпевший(ая) " + fio + " заявил(а):");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc,
                "Права потерпевшего, предусмотренные ст.71 УПК РК, мне разъяснены, от дачи показаний я не отказываюсь, "
                        + "желаю давать показания на " + language + " языке, которым владею свободно, "
                        + "в услугах переводчика не нуждаюсь.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Потерпевший(ая)", fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "По существу заданных вопросов могу показать следующее:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Потерпевший(ая)", fio,
                "Вопрос следователя", "Ответ", "Ответ не получен.");

        addJustifiedParagraph(doc,
                "С целью уточнения и дополнения показаний потерпевшего, ему заданы следующие вопросы:");
        addEmptyLine(doc);
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc, "Более мне пояснить нечего.");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc, "С моих слов напечатано верно и мною прочитано.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Потерпевший(ая)", fio);
        addEmptyLine(doc);

        addInvestigatorBlock(doc, "Допросил, протокол составил:",
                data.getInvestigatorProfession(), formatFio(data.getInvestigator()));
    }
}