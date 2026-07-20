package org.di.digital.model.enums;

public enum InterrogationSpecialGround {
    NONE,
    PREGNANCY,
    MINOR_CHILD,
    FEMALE_58,
    MALE_63,
    OTHER;

    public boolean isSpecial() {
        return this != NONE;
    }
}