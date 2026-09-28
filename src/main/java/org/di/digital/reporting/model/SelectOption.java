package org.di.digital.reporting.model;

import lombok.*;

/**
 * Allowed value of a SELECT column: {@code value} is stored in cells, {@code label} is shown to users.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectOption {
    private String value;
    private String label;
}
