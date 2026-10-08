package org.di.digital.reporting.model;

import lombok.*;
import org.di.digital.reporting.model.enums.ColumnType;

import java.util.ArrayList;
import java.util.List;

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
    @Builder.Default
    private List<SelectOption> options = new ArrayList<>();
    private Integer order;
}
