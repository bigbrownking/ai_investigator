package org.di.digital.service.export.ru;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.model.User;
import org.di.digital.service.export.InterrogationProtocolBuilder;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Component;

import static org.di.digital.util.UserUtil.getCurrentUser;

/**
 * Протокол допроса свидетеля, имеющего право на защиту (обычный и дополнительный).
 * role="witness_protected", isDop=false/true
 */
@Component("ruWitnessProtectedBuilder")
public class RuWitnessProtectedBuilder extends RuBaseBuilder implements InterrogationProtocolBuilder {

    private static final String ROLE_LABEL = "Свидетель, имеющий право на защиту";

    public RuWitnessProtectedBuilder(LocalizationHelper localizationHelper) {
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
        addCenteredParagraph(doc, "допроса свидетеля, имеющего право на защиту", 12);
        addEmptyLine(doc);
        addCityDateBlock(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLine(user, data)
                        + ", с соблюдением требований ст.ст.65-1, 80, 81, 110, 115, 197, 199, 208-210, 212, 214 УПК РК"
                        + ", с участием защитника, представившего уведомление №" + safe(data.getNotificationNumber())
                        + " от " + safe(data.getNotificationDate())
                        + ", допросил по уголовному делу №" + safe(data.getCaseNumber())
                        + " в качестве свидетеля, имеющего право на защиту:");
        addEmptyLine(doc);
        addPersonDataTable(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Перед началом следственного действия участникам объявлен порядок производства допроса.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, ROLE_LABEL, fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Перед началом следственного действия участникам сообщено, что " + fio
                        + " будет допрошен(а) по уголовному делу №" + safe(data.getCaseNumber()) + ".");
        addEmptyLine(doc);

        addRightsSection(doc, "Свидетелю, имеющему право на защиту,", fio, "ст.65-1", RIGHTS_ST65_1);

        addJustifiedParagraph(doc,
                "Свидетелю, имеющему право на защиту, разъяснено право отказаться от дачи показаний, "
                        + "уличающих в совершении уголовного правонарушения его самого, супруга (супруги), близких родственников.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, ROLE_LABEL, fio);
        addEmptyLine(doc);

        addLanguageBlock(doc, data, ROLE_LABEL, fio);

        addJustifiedParagraph(doc,
                "Свидетелю, имеющему право на защиту, предложено рассказать об отношениях, в которых он состоит "
                        + "с подозреваемым(ой), а также по существу известных ему обстоятельств, имеющих значение для дела, "
                        + "на что " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), ROLE_LABEL, fio,
                "Вопрос следователя", "Ответ", "Ответ не получен.");

        addJustifiedParagraph(doc,
                "На этом допрос свидетеля, имеющего право на защиту, окончен. "
                        + "Участникам допроса разъяснено право ознакомиться с результатами допроса, "
                        + "а также право внесения в протокол заявлений, замечаний, дополнений, уточнений и исправлений.");
        addEmptyLine(doc);

        addJustifiedItalicParagraph(doc,
                "Протокол допроса записан правильно. Заявлений, замечаний, дополнений, уточнений и исправлений, "
                        + "подлежащих внесению в протокол, не имею(ем).");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, ROLE_LABEL, fio);
        addEmptyLine(doc);

        addInvestigatorBlock(doc, "Допросил, протокол составил:",
                data.getInvestigatorProfession(), formatFio(data.getInvestigator()));
    }

    private void buildDop(XWPFDocument doc, CaseInterrogationFullResponse data) {
        User user = getCurrentUser();
        String fio = formatFio(data.getFio());

        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "дополнительного допроса свидетеля, имеющего право на защиту", 12);
        addEmptyLine(doc);
        addCityDateBlock(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLine(user, data)
                        + ", с соблюдением требований ст.ст.65-1, 80, 81, 110, 115, 197, 199, 208-212, 214 УПК РК"
                        + ", дополнительно допросил по уголовному делу №" + safe(data.getCaseNumber())
                        + " в качестве свидетеля, имеющего право на защиту " + fio + ".");
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Перед началом следственного действия участникам объявлен порядок производства дополнительного допроса.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, ROLE_LABEL, fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Перед началом следственного действия, свидетелю, имеющему право на защиту " + fio
                        + " сообщено, что он(а) будет дополнительно допрошен(а) по уголовному делу №"
                        + safe(data.getCaseNumber()) + ".");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Свидетелю, имеющему право на защиту, разъяснено право отказаться от дачи показаний, "
                        + "уличающих в совершении уголовного правонарушения его(ее) самого(ой), супруга (супруги), близких родственников.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, ROLE_LABEL, fio);
        addEmptyLine(doc);

        addLanguageBlock(doc, data, ROLE_LABEL, fio);

        addJustifiedParagraph(doc,
                "Свидетелю, имеющему право на защиту, предложено рассказать о дополнениях или уточнениях ранее данных "
                        + "показаний по обстоятельствам расследуемого дела в силу их недостаточной ясности или неполноты, "
                        + "о которых он изъявил желание сообщить, либо о возникших существенных для дела новых вопросах, "
                        + "на что свидетель, имеющий право на защиту " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), ROLE_LABEL, fio,
                "Вопрос следователя", "Ответ", "Ответ не получен.");

        addJustifiedParagraph(doc,
                "На этом дополнительный допрос свидетеля, имеющего право на защиту, окончен. "
                        + "Участникам допроса разъяснено право ознакомиться с результатами допроса, "
                        + "а также право внесения в протокол заявлений, замечаний, дополнений, уточнений и исправлений.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, ROLE_LABEL, fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Протокол дополнительного допроса свидетеля, имеющего право на защиту предъявлен для ознакомления. "
                        + "С настоящим протоколом допроса ознакомлен(а) путем личного прочтения, оглашения по просьбе участника.");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc,
                "Протокол дополнительного допроса записан правильно. Заявлений, замечаний, дополнений, уточнений и исправлений, "
                        + "подлежащих внесению в протокол, не имею(ем).");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, ROLE_LABEL, fio);
        addEmptyLine(doc);

        addInvestigatorBlock(doc, "Допросил, протокол составил:",
                data.getInvestigatorProfession(), formatFio(data.getInvestigator()));
    }
}