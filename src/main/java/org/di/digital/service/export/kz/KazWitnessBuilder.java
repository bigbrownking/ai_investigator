package org.di.digital.service.export.kz;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.model.User;
import org.di.digital.service.export.InterrogationProtocolBuilder;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Component;

import static org.di.digital.util.UserUtil.getCurrentUser;

/**
 * Протокол допроса свидетеля — казахская версия.
 * role="witness", isDop=false/true
 */
@Component("kazWitnessBuilder")
public class KazWitnessBuilder extends KazBaseBuilder implements InterrogationProtocolBuilder {

    public KazWitnessBuilder(LocalizationHelper localizationHelper) {
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

        addCenteredBoldParagraph(doc, "ХАТТАМА", 14);
        addCenteredParagraph(doc, "куәдан жауап алу туралы", 12);
        addEmptyLine(doc);
        addCityDateBlockKaz(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLineKaz(user, data)
                        + ", ҚР ҚПК-нің 60, 78, 110, 115, 197, 199 ч.3, 208, 209, 210, 212, 214-баптарының "
                        + "талаптарына сәйкес №" + safe(data.getCaseNumber())
                        + " санды қылмыстық іс бойынша төменде аты аталған куәден жауап алдым:");
        addEmptyLine(doc);
        addPersonDataTableKaz(doc, data);
        addEmptyLine(doc);

        addInvolvedBlockKaz(doc, data);
        addJustifiedParagraph(doc, "Тергеу әрекетін жүргізу басталар алдында қатысушыларға жауап алу тәртібі жарияланды.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Куә " + fio + " өзіне №" + safe(data.getCaseNumber())
                        + " санды қылмыстық іс бойынша жауап алынатыны туралы хабарланды.");
        addEmptyLine(doc);

        addRightsSectionKaz(doc, "Куәге", fio, "78", RIGHTS_ST78_KAZ);

        addJustifiedParagraph(doc,
                "Куә " + fio
                        + " ҚР ҚК-нің 420, 421-баптарымен көзделген жалған айғақтар беруден "
                        + "және айғақтар беруден бас тартуы үшін қылмыстық жауапкершілік туралы ескертілді.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Куә", fio);
        addEmptyLine(doc);

        addLanguageBlockKaz(doc, data, "Куә", fio);
        addJustifiedParagraph(doc,
                "Куә өзінің күдіктімен қандай қатынаста екені, сондай-ақ іс үшін маңызы бар өзіне белгілі мән-жайлар "
                        + "туралы айтып беруі ұсынылды, бұған куә " + fio + " мынадай айғақтар берді:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Куә", fio,
                "Тергеушінің сұрағы", "Жауабы", "Жауап алынбады.");
        addClosingSectionKaz(doc, data, "куәден", "Куә", fio);
    }

    private void buildDop(XWPFDocument doc, CaseInterrogationFullResponse data) {
        User user = getCurrentUser();
        String fio = formatFio(data.getFio());

        addCenteredBoldParagraph(doc, "ХАТТАМА", 14);
        addCenteredParagraph(doc, "куәдан қосымша жауап алу туралы", 12);
        addEmptyLine(doc);
        addCityDateBlockKazExtended(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                buildIntroLineKaz(user, data)
                        + ", ҚР ҚПК-нің 78, 115, 126, 197, 199, 208-212, 214-баптарының "
                        + "талаптарына сәйкес №" + safe(data.getCaseNumber())
                        + " санды қылмыстық іс бойынша куә " + fio + " ретінде қосымша жауап алдым.");
        addEmptyLine(doc);

        addInvolvedSpecialistBlockKaz(doc, data);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Тергеу әрекетін жүргізу басталар алдында қатысушыларға қосымша жауап алу тәртібі жарияланды.");
        addJustifiedParagraph(doc,
                "Қатысушыларға ғылыми-техникалық құралдардың қолданылуы туралы хабарланды: " + safe(data.getInvolved()) + ".");
        addEmptyLine(doc);

        addInlineSignatureLine(doc, "Куә", fio);
        addInlineSignatureLine(doc, "Маман(дар)", "");
        addInlineSignatureLine(doc, "Аудармашы", "");
        addEmptyLine(doc);

        addSpecialistTranslatorRightsKaz(doc);

        addJustifiedParagraph(doc,
                "Тергеу әрекетін жүргізу басталар алдында куә " + fio
                        + " өзіне №" + safe(data.getCaseNumber())
                        + " санды қылмыстық іс бойынша қосымша жауап алынатыны туралы хабарланды.");
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Куә " + fio + " ҚР ҚК-нің 420, 421-баптарымен көзделген жалған айғақтар беруден "
                        + "және айғақтар беруден бас тартуы үшін қылмыстық жауапкершілік туралы ескертілді.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Куә", fio);
        addEmptyLine(doc);

        addJustifiedParagraph(doc,
                "Куәге қылмыстық құқық бұзушылық жасағаны үшін өзінің, жұбайының (зайыбының), "
                        + "жақын туыстарының қудалануына әкеп соғатын айғақтар беруден бас тартуға құқығы түсіндірілді.");
        addEmptyLine(doc);
        addInlineSignatureLine(doc, "Куә", fio);
        addEmptyLine(doc);

        addLanguageBlockKaz(doc, data, "Куә", fio);

        addJustifiedParagraph(doc,
                "Куәге күдіктімен қандай қатынаста екені, сондай-ақ іс үшін маңызы бар өзіне белгілі мән-жайлар "
                        + "туралы айтып беруі ұсынылды, бұған куә " + fio + " мынадай айғақтар берді:");
        addEmptyLine(doc);

        addQABlock(doc, data.getQaList(), "Куә", fio,
                "Тергеушінің сұрағы", "Жауабы", "Жауап алынбады.");
        addClosingSectionMultiPartyKaz(doc, data, "куәден қосымша жауап алу", "Куә", fio);
    }
}