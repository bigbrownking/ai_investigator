package org.di.digital.model.osmotr;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.enums.OsmotrProcessingStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "osmotr_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsmotrResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionId;
    private String caseNumber;
    private String originalFileName;
    private String originalFileUrl;
    private String userEmail;

    @Enumerated(EnumType.STRING)
    private OsmotrProcessingStatus status;

    private String reportFile;

    @Column(columnDefinition = "TEXT")
    private String reportTxt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private Long processingDurationSeconds;
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "osmotrResult", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("startPage ASC, endPage ASC")
    @Builder.Default
    private List<OsmotrResultSegment> segments = new ArrayList<>();

    public void addSegment(OsmotrResultSegment segment) {
        segments.add(segment);
        segment.setOsmotrResult(this);
    }

    public void removeSegment(OsmotrResultSegment segment) {
        segments.remove(segment);
        segment.setOsmotrResult(null);
    }
}