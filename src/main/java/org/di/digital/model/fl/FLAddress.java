package org.di.digital.model.fl;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@Builder
@AllArgsConstructor
public class FLAddress {
    private String iin;
    private String reason;
    private String address;
    private String addressType;
    private String addressCode;
}
