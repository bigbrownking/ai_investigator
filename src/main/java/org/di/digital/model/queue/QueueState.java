package org.di.digital.model.queue;

import lombok.*;
import jakarta.persistence.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "queue_state")
public class QueueState {
    @Id
    private String id = "round_robin_state";
    private String lastSelectedUser;

    @Builder.Default
    private List<UserCasePointer> lastSelectedCases = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserCasePointer {
        private String userEmail;
        private Long caseId;
    }
}