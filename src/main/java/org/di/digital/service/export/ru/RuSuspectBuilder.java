package org.di.digital.service.export.ru;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.model.User;
import org.di.digital.service.export.InterrogationProtocolBuilder;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Component;

import static org.di.digital.util.UserUtil.getCurrentUser;

/**
 * Протокол допроса подозреваемого (обычный и дополнительный).
 * role="suspect", isDop=false/true
 */
@Component("ruSuspectBuilder")
public class RuSuspectBuilder extends RuBaseBuilder implements InterrogationProtocolBuilder {

    public RuSuspectBuilder(LocalizationHelper localizationHelper) {
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
        addCenteredParagraph(doc, "допроса подозреваемого", 12);
        addEmptyLine(doc);
        addCityDateBlock(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLine(user, data)
                        + ", с соблюдением требований ст.ст.64, 80, 81, 110, 114 ч.5, 115, 197, 199, 208–210, 212, 216 УПК РК"
                        + ", с участием защитника, представившего уведомление №" + safe(data.getNotificationNumber())
                        + " от " + safe(data.getNotificationDate())
                        + ", допросил по уголовному делу №" + safe(data.getCaseNumber())
                        + " в качестве подозреваемого(ой):");
        addEmptyLine(doc);
        addPersonDataTable(doc, data);
        addEmptyLine(doc);

        addInvolvedBlock(doc, data);
        addJustifiedParagraph(doc, "Перед началом следственного действия участникам объявлен порядок производства допроса.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Подозреваемому(ой) " + fio + " сообщено о предъявленном подозрении по уголовному делу №"
                        + safe(data.getCaseNumber()) + ".");
        addEmptyLine(doc);

        addRightsSection(doc, "Подозреваемому(ой)", fio, "ст.64", RIGHTS_ST64);

        addJustifiedParagraph(doc,
                "Подозреваемый(ая) " + fio
                        + " предупрежден(а) о том, что его(её) показания могут быть использованы в качестве доказательств "
                        + "в уголовном процессе, если он(а) не воспользовался(ась) своим правом отказаться от дачи показаний "
                        + "до начала первого допроса, в том числе и при его(её) последующем отказе от этих показаний.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Подозреваемый(ая)", fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "На вопрос, признает ли подозреваемый(ая) себя виновным(ой) полностью или частично, "
                        + "либо отрицает свою вину в совершении уголовного правонарушения " + fio + " пояснил(а):   "
                        + safe(data.getConfession()));
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Подозреваемый(ая)", fio);
        addEmptyLine(doc);

        addLanguageBlock(doc, data, "Подозреваемый(ая)", fio);

        addJustifiedParagraph(doc,
                "Подозреваемому(ой) предложено рассказать об известных обстоятельствах, имеющих значение для дела, "
                        + "на что подозреваемый(ая) " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Подозреваемый(ая)", fio,
                "Вопрос следователя", "Ответ", "Ответ не получен.");
        addClosingSection(doc, data, "подозреваемого", "Подозреваемый(ая)", fio);
    }

    private void buildDop(XWPFDocument doc, CaseInterrogationFullResponse data) {
        User user = getCurrentUser();
        String fio = formatFio(data.getFio());

        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "дополнительного допроса подозреваемого", 12);
        addEmptyLine(doc);
        addCityDateBlock(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLine(user, data)
                        + ", с соблюдением требований ст.ст.64, 80, 81, 110, 114 ч.5, 115, 197, 199, 208–210, 212, 216 УПК РК"
                        + ", с участием защитника, представившего уведомление №" + safe(data.getNotificationNumber())
                        + " от " + safe(data.getNotificationDate())
                        + ", дополнительно допросил по уголовному делу №" + safe(data.getCaseNumber())
                        + " в качестве подозреваемого(ой):");
        addEmptyLine(doc);
        addPersonDataTable(doc, data);
        addEmptyLine(doc);

        addInvolvedBlock(doc, data);
        addJustifiedParagraph(doc, "Перед началом следственного действия участникам объявлен порядок производства дополнительного допроса.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Подозреваемому(ой) " + fio + " сообщено, что он(а) будет дополнительно допрошен(а) по уголовному делу №"
                        + safe(data.getCaseNumber()) + ".");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Подозреваемый(ая) " + fio
                        + " предупрежден(а) о том, что его(её) показания могут быть использованы в качестве доказательств "
                        + "в уголовном процессе, если он(а) не воспользовался(ась) своим правом отказаться от дачи показаний.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Подозреваемый(ая)", fio);
        addEmptyLine(doc);

        addLanguageBlock(doc, data, "Подозреваемый(ая)", fio);

        addJustifiedParagraph(doc,
                "Подозреваемому(ой) предложено рассказать о дополнениях или уточнениях ранее данных показаний, "
                        + "на что подозреваемый(ая) " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Подозреваемый(ая)", fio,
                "Вопрос следователя", "Ответ", "Ответ не получен.");
        addClosingSection(doc, data, "подозреваемого", "Подозреваемый(ая)", fio);
    }
}