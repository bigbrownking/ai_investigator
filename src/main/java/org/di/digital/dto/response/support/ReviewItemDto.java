package org.di.digital.dto.response.support;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ReviewItemDto {
    private String moduleCode;
    private String moduleName;
    private String message;
    private List<ReviewFileDto> files;
}