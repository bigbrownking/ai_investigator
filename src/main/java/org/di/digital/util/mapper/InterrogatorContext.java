package org.di.digital.util.mapper;

import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.user.User;
import org.di.digital.util.LocalizationHelper;

record InterrogatorContext(
        String investigator, String profession, String administration,
        String region, String address, String city) {

    static InterrogatorContext resolve(CaseInterrogation i, User user, LocalizationHelper loc) {
        var lang = user.getSettings().getLanguage();

        String profession = i.getInvestigatorProfession() != null
                ? i.getInvestigatorProfession()
                : loc.getLocalizedName(user.getProfession(), lang);

        String regionSource = i.getInvestigatorRegion() != null
                ? i.getInvestigatorRegion()
                : loc.getLocalizedName(user.getRegion(), lang);
        String region = loc.getGenitive(regionSource);

        String adminSource = i.getInvestigatorAdministration() != null
                ? i.getInvestigatorAdministration()
                : loc.getLocalizedName(user.getAdministration(), lang);
        String administration = loc.getGenitive(adminSource);

        String address = i.getAddrezz() != null
                ? i.getAddrezz()
                : loc.getLocalizedName(user.getRegion().getAddresses().get(0), lang);

        String city = i.getCity() != null
                ? i.getCity()
                : loc.getLocalizedCity(user.getRegion(), lang);

        String investigator = i.getInvestigator() != null ? i.getInvestigator() : user.getFio();

        return new InterrogatorContext(investigator, profession, administration, region, address, city);
    }
}