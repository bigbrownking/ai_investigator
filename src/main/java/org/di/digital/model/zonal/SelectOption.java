package org.di.digital.model.zonal;

import lombok.*;

@Getter
@Setter
@Builder
@EqualsAndHashCode 
@NoArgsConstructor
@AllArgsConstructor
public class SelectOption {
    private String value;
    private String label;
}
