package org.di.digital.service.export.interrogation.kz;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.di.digital.dto.response.interrogation.CaseInterrogationFullResponse;
import org.di.digital.model.user.User;
import org.di.digital.service.export.interrogation.InterrogationProtocolBuilder;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Component;

/**
 * role="specialist" + isDop=false → Маманнан жауап алу хаттамасы
 * role="specialist" + isDop=true  → Маманнан қосымша жауап алу хаттамасы
 */
@Component("kazSpecialistBuilder")
public class KazSpecialistBuilder extends KazBaseBuilder implements InterrogationProtocolBuilder {

    public KazSpecialistBuilder(LocalizationHelper localizationHelper) {
        super(localizationHelper);
    }

    @Override
    public void build(XWPFDocument doc, CaseInterrogationFullResponse data, User user) {
        if (Boolean.TRUE.equals(data.getIsDop())) buildDop(doc, data, user);
        else buildRegular(doc, data, user);
    }

    private void buildRegular(XWPFDocument doc, CaseInterrogationFullResponse data, User user) {
      //  User user = getCurrentUser();
        String fio = formatFio(data.getFio());

        addCenteredBoldParagraph(doc, "ХАТТАМА", 14);
        addCenteredParagraph(doc, "маманнан жауап алу туралы", 12);
        addEmptyLine(doc);
        addCityDateBlockKazSimple(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLineKaz(user, data)
                        + ", ҚР ҚПК-нің 80, 197, 199, 208-212, 285-баптарының "
                        + "талаптарына сәйкес №" + safe(data.getCaseNumber())
                        + " санды қылмыстық іс бойынша маман " + fio + " ретінде жауап алдым.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Тергеу әрекетін жүргізу басталар алдында маман " + fio
                        + " өзіне бұрын берген қорытындысымен байланысты мән-жайлар бойынша жауап алынатыны, "
                        + "сондай-ақ жауап алу тәртібі туралы хабарланды.");
        addEmptyLine(doc);

        addRightsSectionKaz(doc, "Маманға", fio, "80", RIGHTS_ST80_SPECIALIST_KAZ);

        addJustifiedParagraph(doc,
                "Маман " + fio + " келесі сұрақтар бойынша айғақтар беруі ұсынылды "
                        + "(маманның қорытындысымен, іс үшін маңызды сұрақтармен, қолданылған әдістер мен терминдерді "
                        + "нақтылаумен байланысты мән-жайлар анықталады).");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Маман", fio,
                "Тергеушінің сұрағы", "Жауабы", "Жауап алынбады.");
        addClosingSpecialistKaz(doc, data, "Маман", fio, false);
    }

    private void buildDop(XWPFDocument doc, CaseInterrogationFullResponse data, User user) {
        //User user = getCurrentUser();
        String fio = formatFio(data.getFio());

        addCenteredBoldParagraph(doc, "ХАТТАМА", 14);
        addCenteredParagraph(doc, "маманнан қосымша жауап алу туралы", 12);
        addEmptyLine(doc);
        addCityDateBlockKazSimple(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLineKaz(user, data)
                        + ", ҚР ҚПК-нің 80, 197, 199, 208-212, 285-баптарының "
                        + "талаптарына сәйкес №" + safe(data.getCaseNumber())
                        + " санды қылмыстық іс бойынша маман " + fio + " ретінде қосымша жауап алдым.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Тергеу әрекетін жүргізу басталар алдында маман " + fio
                        + " өзіне бұрын берген қорытындысымен байланысты мән-жайлар бойынша қосымша жауап алынатыны, "
                        + "сондай-ақ жауап алу тәртібі туралы хабарланды.");
        addEmptyLine(doc);

        addRightsSectionKaz(doc, "Маманға", fio, "80", RIGHTS_ST80_SPECIALIST_KAZ);

        addJustifiedParagraph(doc,
                "Маман " + fio + " бұрын берген қорытындысын және айғақтарын толықтыру немесе нақтылау туралы айтып беруі ұсынылды, "
                        + "бұған маман " + fio + " мынадай айғақтар берді:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Маман", fio,
                "Тергеушінің сұрағы", "Жауабы", "Жауап алынбады.");
        addClosingSpecialistKaz(doc, data, "Маман", fio, true);
    }
}