package org.di.digital.dto.response.support;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReviewDto {
    private Long id;
    private String subject;
    private LocalDateTime createdAt;
    private String fio;
    private String region;
    private String profession;
    private List<ReviewItemDto> items;
}
