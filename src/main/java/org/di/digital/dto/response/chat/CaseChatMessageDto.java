package org.di.digital.dto.response.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.di.digital.dto.response.cases.ReferenceDto;
import org.di.digital.model.cases.CaseChatMessage;
import org.di.digital.model.enums.cases.MessageRole;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseChatMessageDto {
    private Long id;
    private MessageRole role;
    private Boolean edited;
    private Boolean selected;
    private String content;
    private LocalDateTime createdDate;
    private boolean complete;
    private List<ReferenceLinkDto> references;

}