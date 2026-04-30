package org.di.digital.service.export.kz;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.model.User;
import org.di.digital.service.export.InterrogationProtocolBuilder;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Component;

import static org.di.digital.util.UserUtil.getCurrentUser;

/**
 * role="suspect" + isDop=false → Күдіктіден жауап алу хаттамасы
 * role="suspect" + isDop=true  → Күдіктіден қосымша жауап алу хаттамасы
 */
@Component("kazSuspectBuilder")
public class KazSuspectBuilder extends KazBaseBuilder implements InterrogationProtocolBuilder {

    public KazSuspectBuilder(LocalizationHelper localizationHelper) {
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
        addCenteredParagraph(doc, "күдіктіден жауап алу туралы", 12);
        addEmptyLine(doc);
        addCityDateBlockKaz(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLineKaz(user, data)
                        + ", ҚР ҚПК-нің 64, 80, 81, 110, 114 ч.5, 115, 197, 199, 208–210, 212, 216-баптарының "
                        + "талаптарына сәйкес, " + safe(data.getNotificationDate())
                        + " жылғы №" + safe(data.getNotificationNumber())
                        + " санды қорғау туралы хабарламасын ұсынған қорғаушының қатысуымен "
                        + "№" + safe(data.getCaseNumber())
                        + " санды қылмыстық іс бойынша күдікті ретінде төменде аты аталғаннан жауап алдым:");
        addEmptyLine(doc);
        addPersonDataTableKaz(doc, data);
        addEmptyLine(doc);

        addInvolvedBlockKaz(doc, data);
        addJustifiedParagraph(doc, "Тергеу әрекетін жүргізу басталар алдында қатысушыларға жауап алу тәртібі жарияланды.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Күдіктіге №" + safe(data.getCaseNumber()) + " санды қылмыстық іс бойынша қойылған күдік туралы хабарланды.");
        addEmptyLine(doc);

        addRightsSectionKaz(doc, "Күдіктіге", fio, "64", RIGHTS_ST64_KAZ);

        addJustifiedParagraph(doc,
                "Күдікті " + fio
                        + " ескертілді: егер ол бірінші жауап алу басталғанға дейін айғақтар беруден бас тарту "
                        + "құқығын пайдаланбаса, оның айғақтары қылмыстық процесте дәлелдемелер ретінде "
                        + "пайдаланылуы мүмкін, оның ішінде кейіннен бұл айғақтардан бас тартса да.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Күдікті", fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Күдікті өзін кінәлі деп толық немесе ішінара мойындайтыны немесе "
                        + "қылмыстық құқық бұзушылық жасағанын жоққа шығаратыны туралы сұраққа "
                        + fio + " мынадай түсініктеме берді:   " + safe(data.getConfession()));
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Күдікті", fio);
        addEmptyLine(doc);

        addLanguageBlockKaz(doc, data, "Күдікті", fio);

        addJustifiedParagraph(doc,
                "Күдіктіге іс үшін маңызы бар өзіне белгілі мән-жайлар туралы айтып беруі ұсынылды, "
                        + "бұған күдікті " + fio + " мынадай айғақтар берді:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Күдікті", fio,
                "Тергеушінің сұрағы", "Жауабы", "Жауап алынбады.");
        addClosingSectionKaz(doc, data, "күдіктіден", "Күдікті", fio);
    }

    private void buildDop(XWPFDocument doc, CaseInterrogationFullResponse data) {
        User user = getCurrentUser();
        String fio = formatFio(data.getFio());

        addCenteredBoldParagraph(doc, "ХАТТАМА", 14);
        addCenteredParagraph(doc, "күдіктіден қосымша жауап алу туралы", 12);
        addEmptyLine(doc);
        addCityDateBlockKaz(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLineKaz(user, data)
                        + ", ҚР ҚПК-нің 64, 80, 81, 110, 114 ч.5, 115, 197, 199, 208–210, 212, 216-баптарының "
                        + "талаптарына сәйкес, " + safe(data.getNotificationDate())
                        + " жылғы №" + safe(data.getNotificationNumber())
                        + " санды қорғау туралы хабарламасын ұсынған қорғаушының қатысуымен "
                        + "№" + safe(data.getCaseNumber())
                        + " санды қылмыстық іс бойынша күдікті ретінде төменде аты аталғаннан қосымша жауап алдым:");
        addEmptyLine(doc);
        addPersonDataTableKaz(doc, data);
        addEmptyLine(doc);

        addInvolvedBlockKaz(doc, data);
        addJustifiedParagraph(doc, "Тергеу әрекетін жүргізу басталар алдында қатысушыларға қосымша жауап алу тәртібі жарияланды.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Күдіктіге №" + safe(data.getCaseNumber()) + " санды қылмыстық іс бойынша қосымша жауап алынатыны туралы хабарланды.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Күдікті " + fio
                        + " ескертілді: егер ол айғақтар беруден бас тарту құқығын пайдаланбаса, "
                        + "оның айғақтары қылмыстық процесте дәлелдемелер ретінде пайдаланылуы мүмкін.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Күдікті", fio);
        addEmptyLine(doc);

        addLanguageBlockKaz(doc, data, "Күдікті", fio);

        addJustifiedParagraph(doc,
                "Күдіктіге бұрын берілген айғақтарын толықтырулар немесе нақтылаулар туралы айтып беруі ұсынылды, "
                        + "бұған күдікті " + fio + " мынадай айғақтар берді:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Күдікті", fio,
                "Тергеушінің сұрағы", "Жауабы", "Жауап алынбады.");
        addClosingSectionKaz(doc, data, "күдіктіден", "Күдікті", fio);
    }
}