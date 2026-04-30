package org.di.digital.service.export.kz;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.model.User;
import org.di.digital.service.export.InterrogationProtocolBuilder;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Component;

import static org.di.digital.util.UserUtil.getCurrentUser;

/**
 * role="expert" + isDop=false → Сарапшыдан жауап алу хаттамасы
 * role="expert" + isDop=true  → Сарапшыдан қосымша жауап алу хаттамасы
 */
@Component("kazExpertBuilder")
public class KazExpertBuilder extends KazBaseBuilder implements InterrogationProtocolBuilder {

    public KazExpertBuilder(LocalizationHelper localizationHelper) {
        super(localizationHelper);
    }

    @Override
    public void build(XWPFDocument doc, CaseInterrogationFullResponse data) {
        if (Boolean.TRUE.equals(data.getIsDop())) buildDop(doc, data);
        else buildRegular(doc, data);
    }

    private void buildRegular(XWPFDocument doc, CaseInterrogationFullResponse data) {
        User user = getCurrentUser();
        String fio = formatFio(data.getFio());

        addCenteredBoldParagraph(doc, "ХАТТАМА", 14);
        addCenteredParagraph(doc, "сарапшыдан жауап алу туралы", 12);
        addEmptyLine(doc);
        addCityDateBlockKazSimple(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLineKaz(user, data)
                        + ", ҚР ҚПК-нің 82, 197, 199, 208-212, 286-баптарының "
                        + "талаптарына сәйкес №" + safe(data.getCaseNumber())
                        + " санды қылмыстық іс бойынша сарапшы " + fio + " ретінде жауап алдым.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Тергеу әрекетін жүргізу басталар алдында сарапшы " + fio
                        + " өзіне бұрын берген қорытындысымен байланысты мән-жайлар бойынша жауап алынатыны, "
                        + "сондай-ақ жауап алу тәртібі туралы хабарланды.");
        addEmptyLine(doc);

        addRightsSectionKaz(doc, "Сарапшыға", fio, "82", RIGHTS_ST82_EXPERT_KAZ);

        addJustifiedParagraph(doc,
                "Сарапшы " + fio + " келесі сұрақтар бойынша айғақтар беруі ұсынылды "
                        + "(сарапшының қорытындысымен, қолданылған әдістер мен терминдерді нақтылаумен, "
                        + "қосымша зерттеулерді қажет етпейтін іс үшін маңызды сұрақтармен байланысты мән-жайлар анықталады).");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Сарапшы", fio,
                "Тергеушінің сұрағы", "Жауабы", "Жауап алынбады.");
        addClosingSpecialistKaz(doc, data, "Сарапшы", fio, false);
    }

    private void buildDop(XWPFDocument doc, CaseInterrogationFullResponse data) {
        User user = getCurrentUser();
        String fio = formatFio(data.getFio());

        addCenteredBoldParagraph(doc, "ХАТТАМА", 14);
        addCenteredParagraph(doc, "сарапшыдан қосымша жауап алу туралы", 12);
        addEmptyLine(doc);
        addCityDateBlockKazSimple(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLineKaz(user, data)
                        + ", ҚР ҚПК-нің 82, 197, 199, 208-212, 286-баптарының "
                        + "талаптарына сәйкес №" + safe(data.getCaseNumber())
                        + " санды қылмыстық іс бойынша сарапшы " + fio + " ретінде қосымша жауап алдым.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Тергеу әрекетін жүргізу басталар алдында сарапшы " + fio
                        + " өзіне бұрын берген қорытындысымен байланысты мән-жайлар бойынша қосымша жауап алынатыны, "
                        + "сондай-ақ жауап алу тәртібі туралы хабарланды.");
        addEmptyLine(doc);

        addRightsSectionKaz(doc, "Сарапшыға", fio, "82", RIGHTS_ST82_EXPERT_KAZ);

        addJustifiedParagraph(doc,
                "Сарапшы " + fio + " бұрын берген қорытындысын және айғақтарын толықтыру немесе нақтылау туралы айтып беруі ұсынылды, "
                        + "бұған сарапшы " + fio + " мынадай айғақтар берді:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Сарапшы", fio,
                "Тергеушінің сұрағы", "Жауабы", "Жауап алынбады.");
        addClosingSpecialistKaz(doc, data, "Сарапшы", fio, true);
    }
}