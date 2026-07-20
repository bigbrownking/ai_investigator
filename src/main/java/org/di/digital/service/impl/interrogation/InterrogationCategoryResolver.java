package org.di.digital.service.impl.interrogation;

import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.enums.InterrogationSpecialGround;
import org.di.digital.model.fl.FLRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;

@Slf4j
@Component
public class InterrogationCategoryResolver {

    private static final String MALE = "1";
    private static final String FEMALE = "2";

    public InterrogationSpecialGround resolveFromFl(FLRecord fl) {
        if (fl == null || fl.getBirthDate() == null
                || fl.getSexId() == null || fl.getSexId().isBlank()) {
            return null;
        }
        String sex = fl.getSexId().trim();
        int age = Period.between(fl.getBirthDate(), LocalDate.now()).getYears();

        if (MALE.equals(sex)) {
            return age >= 63 ? InterrogationSpecialGround.MALE_63 : InterrogationSpecialGround.NONE;
        }
        if (FEMALE.equals(sex)) {
            return age >= 58 ? InterrogationSpecialGround.FEMALE_58 : InterrogationSpecialGround.NONE;
        }
        return null;
    }
}