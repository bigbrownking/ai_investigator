package org.di.digital.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a chat session for a specific case
 * One-to-one relationship with Case
 */
@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "case_chats")
@EntityListeners(AuditingEntityListener.class)
public class CaseChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Case caseEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Builder.Default
    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdDate ASC")
    private List<CaseChatMessage> messages = new ArrayList<>();

    /**
     * Add message to chat and update last message timestamp
     */
    public void addMessage(CaseChatMessage message) {
        messages.add(message);
        message.setChat(this);
        this.lastMessageAt = LocalDateTime.now();
    }

    /**
     * Get message count
     */
    public int getMessageCount() {
        return messages.size();
    }
}
