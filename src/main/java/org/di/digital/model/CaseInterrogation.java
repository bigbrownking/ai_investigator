package org.di.digital.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "case_interrogations")
public class CaseInterrogation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String iin;
    private String fio;
    private String role;
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    private CaseInterrogationStatusEnum status;

    @ManyToOne
    @JoinColumn(name = "case_id")
    private Case caseEntity;
}
