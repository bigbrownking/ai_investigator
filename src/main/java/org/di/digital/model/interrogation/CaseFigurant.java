package org.di.digital.model.interrogation;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.cases.Case;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "figurants")
public class CaseFigurant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String externalId;

    private String documentType;
    private String number;
    private String fio;
    private String role;

    @Column(columnDefinition = "TEXT")
    private String details;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private Case caseEntity;

    @Builder.Default
    @OneToMany(mappedBy = "figurant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseFigurantReference> references = new ArrayList<>();
}
