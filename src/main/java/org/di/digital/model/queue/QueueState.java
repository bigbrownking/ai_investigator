package org.di.digital.model.queue;

import lombok.*;
import jakarta.persistence.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

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
    private Map<String, Long> lastSelectedCaseByUser = new HashMap<>();
}
