package org.di.digital.service.export.ru;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.model.User;
import org.di.digital.service.export.InterrogationProtocolBuilder;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Component;

import static org.di.digital.util.UserUtil.getCurrentUser;

/**
 * Протокол допроса специалиста (обычный и дополнительный).
 * role="specialist", isDop=false/true
 */
@Component("ruSpecialistBuilder")
public class RuSpecialistBuilder extends RuBaseBuilder implements InterrogationProtocolBuilder {

    public RuSpecialistBuilder(LocalizationHelper localizationHelper) {
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
        addCenteredParagraph(doc, "о допросе специалиста", 12);
        addEmptyLine(doc);
        addCityDateBlockSimple(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLine(user, data)
                        + ", с соблюдением требований ст.ст.80, 197, 199, 208-212, 285 УПК РК"
                        + ", допросил по уголовному делу №" + safe(data.getCaseNumber())
                        + " в качестве специалиста " + fio + ".");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Перед началом следственного действия специалисту " + fio
                        + " сообщено, что он(а) будет допрошен(а) по обстоятельствам, связанным с ранее данным им заключением, "
                        + "а также ему объявлен порядок производства допроса.");
        addEmptyLine(doc);

        addRightsSection(doc, "Специалисту", fio, "ст.80", RIGHTS_ST80_SPECIALIST);

        addJustifiedParagraph(doc,
                "Специалисту " + fio + " предложено дать показания по следующим вопросам "
                        + "(выясняются обстоятельства, связанные с заключением специалиста, "
                        + "существенными для дела вопросами, уточнением применённых методов и использованных терминов).");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Специалист", fio,
                "Вопрос следователя", "Ответ", "Ответ не получен.");

        addClosingSpecialist(doc, data, "Специалист", fio, false);
    }

    private void buildDop(XWPFDocument doc, CaseInterrogationFullResponse data) {
        User user = getCurrentUser();
        String fio = formatFio(data.getFio());

        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "о дополнительном допросе специалиста", 12);
        addEmptyLine(doc);
        addCityDateBlockSimple(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLine(user, data)
                        + ", с соблюдением требований ст.ст.80, 197, 199, 208-212, 285 УПК РК"
                        + ", дополнительно допросил по уголовному делу №" + safe(data.getCaseNumber())
                        + " в качестве специалиста " + fio + ".");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Перед началом следственного действия специалисту " + fio
                        + " сообщено, что он(а) будет дополнительно допрошен(а) по обстоятельствам, "
                        + "связанным с ранее данным им заключением, а также ему объявлен порядок производства допроса.");
        addEmptyLine(doc);

        addRightsSection(doc, "Специалисту", fio, "ст.80", RIGHTS_ST80_SPECIALIST);

        addJustifiedParagraph(doc,
                "Специалисту " + fio + " предложено рассказать о дополнениях или уточнениях ранее данного заключения "
                        + "и показаний по обстоятельствам, имеющим значение для дела, "
                        + "на что специалист " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Специалист", fio,
                "Вопрос следователя", "Ответ", "Ответ не получен.");

        addClosingSpecialist(doc, data, "Специалист", fio, true);
    }
}