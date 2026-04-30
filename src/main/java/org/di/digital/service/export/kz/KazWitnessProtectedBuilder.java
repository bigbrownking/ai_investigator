package org.di.digital.service.export.kz;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.model.User;
import org.di.digital.service.export.InterrogationProtocolBuilder;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Component;

import static org.di.digital.util.UserUtil.getCurrentUser;

/**
 * role="witness_protected" + isDop=false → Қорғалуға құқығы бар куәдан жауап алу хаттамасы
 * role="witness_protected" + isDop=true  → Қорғалуға құқығы бар куәден қосымша жауап алу хаттамасы
 */
@Component("kazWitnessProtectedBuilder")
public class KazWitnessProtectedBuilder extends KazBaseBuilder implements InterrogationProtocolBuilder {

    private static final String ROLE_LABEL = "Қорғалуға құқығы бар куә";

    public KazWitnessProtectedBuilder(LocalizationHelper localizationHelper) {
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
        addCenteredParagraph(doc, "қорғалуға құқығы бар куәдан жауап алу туралы", 12);
        addEmptyLine(doc);
        addCityDateBlockKazExtended(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLineKaz(user, data)
                        + ", ҚР ҚПК-нің 65-1, 80, 81, 110, 115, 197, 199, 208-210, 212, 214-баптарының "
                        + "талаптарына сәйкес, " + safe(data.getNotificationDate())
                        + " жылғы №" + safe(data.getNotificationNumber())
                        + " санды қорғау туралы хабарламасын ұсынған қорғаушының қатысуымен "
                        + "№" + safe(data.getCaseNumber())
                        + " санды қылмыстық іс бойынша қорғалуға құқығы бар куә ретінде төменде аты аталғаннан жауап алдым:");
        addEmptyLine(doc);
        addPersonDataTableKaz(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Тергеу әрекетін жүргізу басталар алдында қатысушыларға жауап алу тәртібі жарияланды.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, ROLE_LABEL, fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Тергеу әрекетін жүргізу басталар алдында қатысушыларға " + fio
                        + " өзіне №" + safe(data.getCaseNumber()) + " санды қылмыстық іс бойынша жауап алынатыны туралы хабарланды.");
        addEmptyLine(doc);

        addRightsSectionKaz(doc, "Қорғалуға құқығы бар куәге", fio, "65-1", RIGHTS_ST65_1_KAZ);

        addJustifiedParagraph(doc,
                "Қорғалуға құқығы бар куәге қылмыстық құқық бұзушылық жасағаны үшін өзінің, жұбайының (зайыбының), "
                        + "жақын туыстарының қудалануына әкеп соғатын айғақтар беруден бас тартуға құқығы түсіндірілді.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, ROLE_LABEL, fio);
        addEmptyLine(doc);

        addLanguageBlockKaz(doc, data, ROLE_LABEL, fio);

        addJustifiedParagraph(doc,
                "Қорғалуға құқығы бар куәге күдіктімен қандай қатынаста екені, сондай-ақ іс үшін маңызы бар "
                        + "өзіне белгілі мән-жайлар туралы айтып беруі ұсынылды, бұған " + fio + " мынадай айғақтар берді:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), ROLE_LABEL, fio,
                "Тергеушінің сұрағы", "Жауабы", "Жауап алынбады.");

        addJustifiedParagraph(doc,
                "Осымен қорғалуға құқығы бар куәден жауап алу аяқталды. "
                        + "Жауап алуға қатысушыларға жауап алу нәтижелерімен танысу, сондай-ақ хаттамаға "
                        + "өтініштер, ескертулер, толықтырулар, нақтылаулар мен түзетулер енгізу құқықтары түсіндірілді.");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc,
                "Жауап алу хаттамасы дұрыс жазылды. Хаттамаға енгізуге жататын өтініштер, ескертулер, "
                        + "толықтырулар, нақтылаулар мен түзетулер жоқ.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, ROLE_LABEL, fio);
        addEmptyLine(doc);
        addInvestigatorBlock(doc, "Жауап алды, хаттаманы жасады:",
                data.getInvestigatorProfession(), formatFio(data.getInvestigator()));
    }

    private void buildDop(XWPFDocument doc, CaseInterrogationFullResponse data) {
        User user = getCurrentUser();
        String fio = formatFio(data.getFio());

        addCenteredBoldParagraph(doc, "ХАТТАМА", 14);
        addCenteredParagraph(doc, "қорғалуға құқығы бар куәден қосымша жауап алу туралы", 12);
        addEmptyLine(doc);
        addCityDateBlockKazExtended(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLineKaz(user, data)
                        + ", ҚР ҚПК-нің 65-1, 80, 81, 110, 115, 197, 199, 208-212, 214-баптарының "
                        + "талаптарына сәйкес №" + safe(data.getCaseNumber())
                        + " санды қылмыстық іс бойынша қорғалуға құқығы бар куә " + fio + " ретінде қосымша жауап алдым.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Тергеу әрекетін жүргізу басталар алдында қатысушыларға қосымша жауап алу тәртібі жарияланды.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, ROLE_LABEL, fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Тергеу әрекетін жүргізу басталар алдында, қорғалуға құқығы бар куә " + fio
                        + " өзіне №" + safe(data.getCaseNumber()) + " санды қылмыстық іс бойынша қосымша жауап алынатыны туралы хабарланды.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Қорғалуға құқығы бар куәге қылмыстық құқық бұзушылық жасағаны үшін өзінің, жұбайының (зайыбының), "
                        + "жақын туыстарының қудалануына әкеп соғатын айғақтар беруден бас тартуға құқығы түсіндірілді.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, ROLE_LABEL, fio);
        addEmptyLine(doc);

        addLanguageBlockKaz(doc, data, ROLE_LABEL, fio);

        addJustifiedParagraph(doc,
                "Қорғалуға құқығы бар куәге бұрын берілген айғақтарын толықтыру немесе нақтылау туралы айтып беруі ұсынылды, "
                        + "бұған қорғалуға құқығы бар куә " + fio + " мынадай айғақтар берді:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), ROLE_LABEL, fio,
                "Тергеушінің сұрағы", "Жауабы", "Жауап алынбады.");

        addJustifiedParagraph(doc,
                "Осымен қорғалуға құқығы бар куәден қосымша жауап алу аяқталды. "
                        + "Жауап алуға қатысушыларға жауап алу нәтижелерімен танысу, сондай-ақ хаттамаға "
                        + "өтініштер, ескертулер, толықтырулар, нақтылаулар мен түзетулер енгізу құқықтары түсіндірілді.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, ROLE_LABEL, fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Қорғалуға құқығы бар куәден қосымша жауап алу хаттамасы танысу үшін ұсынылды. "
                        + "Осы жауап алу хаттамасымен жеке оқып шығу, қатысушының өтінісі бойынша жария ету жолымен танысты.");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc,
                "Қосымша жауап алу хаттамасы дұрыс жазылды. Хаттамаға енгізуге жататын өтініштер, ескертулер, "
                        + "толықтырулар, нақтылаулар мен түзетулер жоқ.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, ROLE_LABEL, fio);
        addEmptyLine(doc);
        addInvestigatorBlock(doc, "Жауап алды, хаттаманы жасады:",
                data.getInvestigatorProfession(), formatFio(data.getInvestigator()));
    }
}