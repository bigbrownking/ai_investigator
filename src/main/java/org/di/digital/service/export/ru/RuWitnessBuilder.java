package org.di.digital.service.export.ru;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.model.User;
import org.di.digital.service.export.InterrogationProtocolBuilder;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Component;

import static org.di.digital.util.UserUtil.getCurrentUser;

/**
 * Протокол допроса свидетеля (обычный и дополнительный).
 * role="witness", isDop=false/true
 */
@Component("ruWitnessBuilder")
public class RuWitnessBuilder extends RuBaseBuilder implements InterrogationProtocolBuilder {

    public RuWitnessBuilder(LocalizationHelper localizationHelper) {
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

    // ── Обычный допрос свидетеля ──────────────────────────────────────────────

    private void buildRegular(XWPFDocument doc, CaseInterrogationFullResponse data) {
        User user = getCurrentUser();
        String fio = formatFio(data.getFio());

        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "допроса свидетеля", 12);
        addEmptyLine(doc);
        addCityDateBlock(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLine(user, data)
                        + ", с соблюдением требований ст.ст.78, 80, 81, 110, 115, 126, 197, 199, 208 – 210, 212, 214 УПК РК"
                        + " допросил по уголовному делу №" + safe(data.getCaseNumber())
                        + " в качестве свидетеля:");
        addEmptyLine(doc);
        addPersonDataTable(doc, data);
        addEmptyLine(doc);

        addInvolvedBlock(doc, data);
        addJustifiedParagraph(doc, "Перед началом следственного действия участникам объявлен порядок производства допроса. ");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Свидетелю " + fio + " сообщено, что он(а) будет допрошен(а) по уголовному делу №"
                        + safe(data.getCaseNumber()) + ".");
        addEmptyLine(doc);

        addRightsSection(doc, "Свидетелю", fio, "ст.78", RIGHTS_ST78);

        addJustifiedParagraph(doc,
                "Свидетель " + fio
                        + " предупрежден(а) об уголовной ответственности за заведомо ложные показания "
                        + "и за отказ от дачи показаний, предусмотренными ст.ст.420, 421 УК РК.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Свидетель", fio);
        addEmptyLine(doc);

        addLanguageBlock(doc, data, "Свидетелю", fio);
        addJustifiedParagraph(doc,
                "Свидетелю предложено рассказать об отношениях, в которых он(а) состоит с подозреваемым(ой), "
                        + "а также по существу известных ему обстоятельств, имеющих значение для дела, "
                        + "на что свидетель " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Свидетель", fio,
                "Вопрос следователя", "Ответ", "Ответ не получен.");
        addClosingSection(doc, data, "свидетеля", "Свидетель", fio);
    }

    // ── Дополнительный допрос свидетеля ──────────────────────────────────────

    private void buildDop(XWPFDocument doc, CaseInterrogationFullResponse data) {
        User user = getCurrentUser();
        String fio = formatFio(data.getFio());

        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "дополнительного допроса свидетеля", 12);
        addEmptyLine(doc);
        addCityDateBlock(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLine(user, data)
                        + ", с соблюдением требований ст.ст.78, 115, 126, 197, 199, 208-212, 214 УПК РК"
                        + ", дополнительно допросил по уголовному делу №" + safe(data.getCaseNumber())
                        + " в качестве свидетеля " + fio + ".");
        addEmptyLine(doc);

        addInvolvedSpecialistBlock(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Перед началом следственного действия участникам объявлен порядок производства дополнительного допроса.");
        addJustifiedParagraph(doc,
                "Участники уведомлены о применении научно-технических средств: " + safe(data.getInvolved()) + ".");
        addEmptyLine(doc);

        addInlineSignatureLine(doc, "Свидетель", fio);
        addInlineSignatureLine(doc, "Специалист(ы)", "");
        addInlineSignatureLine(doc, "Переводчик", "");
        addEmptyLine(doc);

        addSpecialistTranslatorRights(doc);

        addJustifiedParagraph(doc,
                "Перед началом следственного действия свидетелю " + fio
                        + " сообщено, что он(а) будет дополнительно допрошен(а) по уголовному делу №"
                        + safe(data.getCaseNumber()) + ".");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Свидетель " + fio
                        + " предупрежден(а) об уголовной ответственности за заведомо ложные показания "
                        + "и за отказ от дачи показаний, предусмотренными ст.ст.420, 421 УК РК.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Свидетель", fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Свидетелю разъяснено право отказаться от дачи показаний, уличающих в совершении "
                        + "уголовного правонарушения его самого, супруга (супруги), близких родственников.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Свидетель", fio);
        addEmptyLine(doc);

        addLanguageBlock(doc, data, "Свидетель", fio);

        addJustifiedParagraph(doc,
                "Свидетелю предложено рассказать об отношениях, в которых он состоит с подозреваемым(ой), "
                        + "а также по существу известных ему обстоятельств, имеющих значение для дела, "
                        + "на что свидетель " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Свидетель", fio,
                "Вопрос следователя", "Ответ", "Ответ не получен.");
        addClosingSectionMultiParty(doc, data, "дополнительного допроса свидетеля", "Свидетель", fio);
    }
}