package org.di.digital.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SupportTicketDto {
    private Long id;
    private String message;
    private String phoneNumber;
    private LocalDateTime createdAt;
    private List<SupportTicketPhotoDto> photos;
}
