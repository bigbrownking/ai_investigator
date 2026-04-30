package org.di.digital.service.export.kz;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.model.User;
import org.di.digital.service.export.InterrogationProtocolBuilder;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Component;

import static org.di.digital.util.UserUtil.getCurrentUser;

/**
 * role="victim" + isDop=false → Зардап шеккеннен жауап алу хаттамасы
 * role="victim" + isDop=true  → Зардап шеккеннен қосымша жауап алу хаттамасы
 */
@Component("kazVictimBuilder")
public class KazVictimBuilder extends KazBaseBuilder implements InterrogationProtocolBuilder {

    public KazVictimBuilder(LocalizationHelper localizationHelper) {
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
        addCenteredParagraph(doc, "зардап шеккеннен жауап алу туралы", 12);
        addEmptyLine(doc);
        addCityDateBlockKaz(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLineKaz(user, data)
                        + ", ҚР ҚПК-нің 71, 80, 81, 110, 115, 197, 199, 208–210, 212, 214-баптарының "
                        + "талаптарына сәйкес №" + safe(data.getCaseNumber())
                        + " санды қылмыстық іс бойынша зардап шеккен ретінде төменде аты аталғаннан жауап алдым:");
        addEmptyLine(doc);
        addPersonDataTableKaz(doc, data);
        addEmptyLine(doc);

        addInvolvedBlockKaz(doc, data);
        addJustifiedParagraph(doc, "Тергеу әрекетін жүргізу басталар алдында қатысушыларға жауап алу тәртібі жарияланды.");
        addEmptyLine(doc);

        addRightsSectionKaz(doc, "Зардап шеккенге", fio, "71", RIGHTS_ST71_KAZ);

        addJustifiedParagraph(doc,
                "Зардап шеккен " + fio
                        + " ескертілді: ҚР ҚК-нің 419-бабына сәйкес жалған арыз жасағаны үшін, "
                        + "ҚР ҚК-нің 420-бабына сәйкес жалған айғақтар бергені үшін; "
                        + "ҚР ҚК-нің 421-бабына сәйкес айғақтар беруден бас тартқаны немесе жалтарғаны үшін қылмыстық жауапкершілік туралы.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Зардап шеккен", fio);
        addEmptyLine(doc);

        String language = safe(data.getLanguage()).equals("—") ? "қазақ" : data.getLanguage();
        addJustifiedItalicParagraph(doc, "Содан кейін, зардап шеккен " + fio + " мәлімдеді:");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc,
                "ҚР ҚПК-нің 71-бабымен көзделген зардап шеккеннің құқықтары маған түсіндірілді, "
                        + "айғақтар беруден бас тартпаймын, айғақтарды " + language + " тілінде беруді қалаймын, "
                        + "ол тілді еркін меңгерменмін, аудармашының көмегін қажет етпеймін, "
                        + "бұл менің материалдық жағдайыма байланысты емес, маған қауіпсіздікті қамтамасыз ету шаралары қажет емес.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Зардап шеккен", fio);
        addEmptyLine(doc);
        addJustifiedParagraph(doc, "Қойылған сұрақтардың мәні бойынша мынаны көрсете аламын:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Зардап шеккен", fio,
                "Тергеушінің сұрағы", "Жауабы", "Жауап алынбады.");
        addClosingSectionKaz(doc, data, "зардап шеккеннен", "Зардап шеккен", fio);
    }

    private void buildDop(XWPFDocument doc, CaseInterrogationFullResponse data) {
        User user = getCurrentUser();
        String fio = formatFio(data.getFio());

        addCenteredBoldParagraph(doc, "ХАТТАМА", 14);
        addCenteredParagraph(doc, "зардап шеккеннен қосымша жауап алу туралы", 12);
        addEmptyLine(doc);
        addCityDateBlockKaz(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLineKaz(user, data)
                        + ", ҚР ҚПК-нің 71, 110, 115, 197, 199, 209-212, 214-баптарының "
                        + "талаптарына сәйкес зардап шеккен ретінде №" + safe(data.getCaseNumber())
                        + " санды сотқа дейінгі тергеп-тексеру материалдары бойынша қосымша жауап алдым:");
        addEmptyLine(doc);
        addPersonDataTableKaz(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Жауап алу басталар алдында ҚР ҚПК-нің 110, 214-баптарына сәйкес зардап шеккенге "
                        + "процестік құқықтары мен міндеттері түсіндірілді, оның ішінде өзіне, жұбайына (зайыбына) және "
                        + "жақын туыстарына қарсы айғақтар бермеу құқығы түсіндірілді. Сонымен қатар ҚР ҚПК-нің 71-бабына сәйкес:");
        addEmptyLine(doc);
        addJustifiedParagraph(doc, RIGHTS_ST71_KAZ);
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Зардап шеккен", fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Зардап шеккен " + fio
                        + " ескертілді: ҚР ҚК-нің 419-бабына сәйкес жалған арыз жасағаны үшін, "
                        + "ҚР ҚК-нің 420-бабына сәйкес жалған айғақтар бергені үшін; "
                        + "ҚР ҚК-нің 421-бабына сәйкес айғақтар беруден бас тартқаны немесе жалтарғаны үшін қылмыстық жауапкершілік туралы.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Зардап шеккен", fio);
        addEmptyLine(doc);

        String language = safe(data.getLanguage()).equals("—") ? "қазақ" : data.getLanguage();
        addJustifiedItalicParagraph(doc, "Содан кейін, зардап шеккен " + fio + " мәлімдеді:");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc,
                "ҚР ҚПК-нің 71-бабымен көзделген зардап шеккеннің құқықтары маған түсіндірілді, "
                        + "айғақтар беруден бас тартпаймын, айғақтарды " + language + " тілінде беруді қалаймын, "
                        + "ол тілді еркін меңгерменмін, аудармашының көмегін қажет етпеймін.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Зардап шеккен", fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc, "Қойылған сұрақтардың мәні бойынша мынаны көрсете аламын:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Зардап шеккен", fio,
                "Тергеушінің сұрағы", "Жауабы", "Жауап алынбады.");

        addJustifiedParagraph(doc,
                "Зардап шеккеннің айғақтарын нақтылау және толықтыру мақсатында оған келесі сұрақтар қойылды:");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc, "Одан басқа түсіндіретін ештеңем жоқ.");
        addEmptyLine(doc);
        addJustifiedItalicParagraph(doc, "Менің сөздерімнен дұрыс басылып шықты және мен оқып шықтым.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Зардап шеккен", fio);
        addEmptyLine(doc);
        addInvestigatorBlock(doc, "Жауап алды, хаттаманы жасады:",
                data.getInvestigatorProfession(), formatFio(data.getInvestigator()));
    }
}