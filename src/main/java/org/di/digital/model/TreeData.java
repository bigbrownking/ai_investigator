package org.di.digital.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.enums.TreeModuleType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Slf4j
@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "tree_data",
       indexes = {
           @Index(name = "idx_case_module", columnList = "case_id,module_type"),
           @Index(name = "idx_case_fetched", columnList = "case_id,fetched_at")
       })
@EntityListeners(AuditingEntityListener.class)
public class TreeData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Case caseEntity;

    @Enumerated(EnumType.STRING)
    @Column(name = "module_type", nullable = false, length = 50)
    private TreeModuleType moduleType;

    @Column(name = "json_data", columnDefinition = "JSONB")
    private String jsonData;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "fetch_success", nullable = false)
    @Builder.Default
    private Boolean fetchSuccess = true;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    public void prePersist() {
        if (fetchedAt == null) {
            fetchedAt = LocalDateTime.now();
        }
    }

    public void incrementVersion() {
        this.version = (this.version == null ? 1 : this.version) + 1;
    }
}
