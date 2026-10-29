package org.di.digital.model.enums.zonal;

import com.fasterxml.jackson.annotation.JsonAlias;

public enum ColumnType {
    NUMBER,
    @JsonAlias("TEXT")
    STRING,
    DATE,
    SELECT
}
