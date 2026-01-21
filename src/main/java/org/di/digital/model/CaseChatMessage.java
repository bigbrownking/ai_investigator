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
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_created", columnList = "chat_id,created_date")
})
@EntityListeners(AuditingEntityListener.class)
public class CaseChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private CaseChat chat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    /**
     * Optional: Store metadata like token count, model version, etc.
     */
    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "model_version")
    private String modelVersion;

    /**
     * For streaming messages, track if complete
     */
    @Builder.Default
    @Column(name = "is_complete")
    private boolean complete = true;

    /**
     * Optional: Reference to files used in this message context
     */
    @ManyToMany
    @JoinTable(
            name = "message_files",
            joinColumns = @JoinColumn(name = "message_id"),
            inverseJoinColumns = @JoinColumn(name = "file_id")
    )
    private List<CaseFile> referencedFiles = new ArrayList<>();
}
