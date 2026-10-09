package org.di.digital.model.zonal;

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
