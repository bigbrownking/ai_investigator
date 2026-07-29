package org.di.digital.dto.response.dictionary;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModuleDto {
    private String code;
    private String name;
}