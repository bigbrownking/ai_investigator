package org.di.digital.reporting.model;

import lombok.*;
import org.di.digital.reporting.model.enums.ColumnType;

import java.util.ArrayList;
import java.util.List;

/**
 * One column of a consolidated table; operators fill exactly one cell per column for their region.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnDefinition {
    private String key;
    private String label;
    private ColumnType type;
    private boolean required;
    /** Only for SELECT columns. */
    @Builder.Default
    private List<SelectOption> options = new ArrayList<>();
    private Integer order;
}
