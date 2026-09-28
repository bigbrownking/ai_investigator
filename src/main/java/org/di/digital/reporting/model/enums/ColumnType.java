package org.di.digital.reporting.model.enums;

/**
 * Cell type of a template column. On submit values are normalized:
 * NUMBER -> BigDecimal, STRING -> String, DATE -> ISO yyyy-MM-dd String, SELECT -> option value String.
 */
public enum ColumnType {
    NUMBER,
    STRING,
    DATE,
    SELECT
}
