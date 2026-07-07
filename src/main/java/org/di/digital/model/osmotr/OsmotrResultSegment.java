package org.di.digital.model.osmotr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "osmotr_result_segments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsmotrResultSegment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private Integer startPage;
    private Integer endPage;

    @Column(columnDefinition = "TEXT")
    private String inspectionText;

    @Builder.Default
    private Boolean evidenceNeeded = false;

    @Builder.Default
    private Boolean returnNeeded = false;

    private String fileUrl;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "osmotr_result_id")
    private OsmotrResult osmotrResult;
}