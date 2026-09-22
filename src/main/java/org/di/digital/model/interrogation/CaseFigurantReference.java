package org.di.digital.model.interrogation;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "figurant_references")
public class CaseFigurantReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_id", columnDefinition = "TEXT")
    private String referenceId;

    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "figurant_id")
    private CaseFigurant figurant;
}