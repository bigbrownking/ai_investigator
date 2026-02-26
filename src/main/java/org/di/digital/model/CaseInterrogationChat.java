package org.di.digital.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "case_interrogation_chats")
@EntityListeners(AuditingEntityListener.class)
public class CaseInterrogationChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interrogation_id", nullable = false, unique = true)
    private CaseInterrogation interrogation;

    @Builder.Default
    @Column(name = "is_active")
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Builder.Default
    @OneToMany(mappedBy = "interrogationChat", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdDate ASC")
    private List<CaseChatMessage> messages = new ArrayList<>();

    public void addMessage(CaseChatMessage message) {
        messages.add(message);
        message.setInterrogationChat(this);
        this.lastMessageAt = LocalDateTime.now();
    }
}
