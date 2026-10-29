package org.di.digital.model.enums.osmotr;

import lombok.Getter;

@Getter
public enum OsmotrFileType {
    RETURN("return"),
    EVIDENCE("evidence"),
    RESOLUTION("resolution"),
    REPORT("report");

    private final String value;

    OsmotrFileType(String value) {
        this.value = value;
    }
}
