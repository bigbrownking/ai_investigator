package org.di.digital.dto.response.admin;

import lombok.*;
import org.di.digital.dto.response.user.UserProfile;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedUserResponse {
    private List<UserProfile> content;
    private long totalElements;
    private int totalPages;
    private int number;
    private int size;
    private boolean first;
    private boolean last;
    private boolean empty;
    private long activeUsers;
    private long inactiveUsers;
}
