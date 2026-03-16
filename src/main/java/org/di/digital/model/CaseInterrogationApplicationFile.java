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
@Table(name = "case_interrogation_application_files")
public class CaseInterrogationApplicationFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String originalFileName;
    private String storedFileName;
    private String fileUrl;
    private String contentType;
    private Long fileSize;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interrogation_id")
    private CaseInterrogation interrogation;

    public void addInterrogation(CaseInterrogation interrogation) {
        this.interrogation = interrogation;
        if (!interrogation.getApplicationFiles().contains(this)) {
            interrogation.getApplicationFiles().add(this);
        }
    }
}
