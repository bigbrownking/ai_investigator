package org.di.digital.reporting.model;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectOption {
    private String value;
    private String label;
}
