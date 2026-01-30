package org.di.digital.model;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "logs", indexes = {
        @Index(name = "idx_log_timestamp", columnList = "timestamp"),
        @Index(name = "idx_log_level", columnList = "level"),
        @Index(name = "idx_log_user", columnList = "user_id"),
        @Index(name = "idx_log_case", columnList = "case_id")
})
@EntityListeners(AuditingEntityListener.class)
public class Log {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LogLevel level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private LogAction action;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private Case caseEntity;

    @Column(length = 45)
    private String ipAddress;
}