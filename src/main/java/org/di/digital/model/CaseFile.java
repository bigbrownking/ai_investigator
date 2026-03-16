package org.di.digital.model;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.enums.CaseFileStatusEnum;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "case_files")
public class CaseFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String originalFileName;
    private String storedFileName;
    private String fileUrl;
    private String contentType;
    private Long fileSize;
    private LocalDateTime uploadedAt;
    private LocalDateTime completedAt;
    private boolean isQualification;
    //private int tom;

    @Enumerated(EnumType.STRING)
    private CaseFileStatusEnum status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Case caseEntity;


    public void addCaseEntity(Case caseEntity) {
        this.caseEntity = caseEntity;
        if (!caseEntity.getFiles().contains(this)) {
            caseEntity.getFiles().add(this);
        }
    }
}