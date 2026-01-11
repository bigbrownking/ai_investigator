package org.di.digital.model;

import jakarta.persistence.*;
import lombok.*;

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

    @Enumerated(EnumType.STRING)
    private CaseFileStatusEnum status;

    @ManyToOne
    @JoinColumn(name = "case_id")
    private Case caseEntity;
}