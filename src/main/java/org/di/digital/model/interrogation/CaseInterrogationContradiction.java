package org.di.digital.model.interrogation;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.dto.response.cases.ReferenceDto;
import org.di.digital.dto.response.interrogation.ContradictionResponse;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "case_interrogation_contradictions", indexes = {
        @Index(name = "idx_contradiction_chat", columnList = "interrogation_chat_id"),
        @Index(name = "idx_contradiction_message", columnList = "source_message_id")
})
@EntityListeners(AuditingEntityListener.class)
public class CaseInterrogationContradiction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interrogation_chat_id", nullable = false)
    private CaseInterrogationChat interrogationChat;

    @Column(name = "source_message_id")
    private Long sourceMessageId;

    @Column(name = "indication", columnDefinition = "TEXT")
    private String indication;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    @Column(name = "confidence_percent")
    private Integer confidencePercent;

    @Column(name = "contradiction_references", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<ReferenceDto> references;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;
}