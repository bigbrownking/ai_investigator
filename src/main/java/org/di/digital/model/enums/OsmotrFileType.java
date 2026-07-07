package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum OsmotrFileType {
    RETURN("return"),
    EVIDENCE("evidence"),
    REPORT("report");

    private final String value;

    OsmotrFileType(String value) {
        this.value = value;
    }
}
