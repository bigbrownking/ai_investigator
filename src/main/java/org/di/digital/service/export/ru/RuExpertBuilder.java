package org.di.digital.service.export.ru;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.model.User;
import org.di.digital.service.export.InterrogationProtocolBuilder;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Component;

import static org.di.digital.util.UserUtil.getCurrentUser;

/**
 * Протокол допроса эксперта (обычный и дополнительный).
 * role="expert", isDop=false/true
 */
@Component("ruExpertBuilder")
public class RuExpertBuilder extends RuBaseBuilder implements InterrogationProtocolBuilder {

    public RuExpertBuilder(LocalizationHelper localizationHelper) {
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
        addCenteredParagraph(doc, "о допросе эксперта", 12);
        addEmptyLine(doc);
        addCityDateBlockSimple(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLine(user, data)
                        + ", с соблюдением требований ст.ст.82, 197, 199, 208-212, 286 УПК РК"
                        + ", допросил по уголовному делу №" + safe(data.getCaseNumber())
                        + " в качестве эксперта " + fio + ".");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Перед началом следственного действия эксперту " + fio
                        + " сообщено, что он(а) будет допрошен(а) по обстоятельствам, связанным с ранее данным им заключением, "
                        + "а также ему объявлен порядок производства допроса.");
        addEmptyLine(doc);

        addRightsSection(doc, "Эксперту", fio, "ст.82", RIGHTS_ST82_EXPERT);

        addJustifiedParagraph(doc,
                "Эксперту " + fio + " предложено дать показания по следующим вопросам "
                        + "(выясняются обстоятельства, связанные с заключением эксперта, "
                        + "уточнением применённых методов и использованных терминов, "
                        + "существенными для дела вопросами, не требующими дополнительных исследований).");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Эксперт", fio,
                "Вопрос следователя", "Ответ", "Ответ не получен.");

        addClosingSpecialist(doc, data, "Эксперт", fio, false);
    }

    private void buildDop(XWPFDocument doc, CaseInterrogationFullResponse data) {
        User user = getCurrentUser();
        String fio = formatFio(data.getFio());

        addCenteredBoldParagraph(doc, "ПРОТОКОЛ", 14);
        addCenteredParagraph(doc, "о дополнительном допросе эксперта", 12);
        addEmptyLine(doc);
        addCityDateBlockSimple(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLine(user, data)
                        + ", с соблюдением требований ст.ст.82, 197, 199, 208-212, 286 УПК РК"
                        + ", дополнительно допросил по уголовному делу №" + safe(data.getCaseNumber())
                        + " в качестве эксперта " + fio + ".");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Перед началом следственного действия эксперту " + fio
                        + " сообщено, что он(а) будет дополнительно допрошен(а) по обстоятельствам, "
                        + "связанным с ранее данным им заключением, а также ему объявлен порядок производства допроса.");
        addEmptyLine(doc);

        addRightsSection(doc, "Эксперту", fio, "ст.82", RIGHTS_ST82_EXPERT);

        addJustifiedParagraph(doc,
                "Эксперту " + fio + " предложено рассказать о дополнениях или уточнениях ранее данного заключения "
                        + "и показаний по обстоятельствам, имеющим значение для дела, "
                        + "на что эксперт " + fio + " дал(а) следующие показания:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Эксперт", fio,
                "Вопрос следователя", "Ответ", "Ответ не получен.");

        addClosingSpecialist(doc, data, "Эксперт", fio, true);
    }
}