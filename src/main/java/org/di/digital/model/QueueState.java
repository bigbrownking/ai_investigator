package org.di.digital.model;

import lombok.*;
import jakarta.persistence.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
}
