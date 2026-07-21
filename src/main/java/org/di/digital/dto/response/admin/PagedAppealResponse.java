package org.di.digital.dto.response.admin;

import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedAppealResponse {
    private List<AppealDto> content;
    private long totalElements;
    private int totalPages;
    private int number;
    private int size;
    private boolean first;
    private boolean last;
    private boolean empty;
    private long pendingAppeals;
    private long approvedAppeals;
    private long rejectedAppeals;
}
