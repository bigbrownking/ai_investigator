package org.di.digital.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "case_interrogation_protocols")
public class CaseInterrogationProtocol {
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    private String sexId;
    private String fio;
    private String dateOfBirth;
    private String birthPlace;
    private String citizenship;
    private String nationality;

    @Builder.Default
    @OneToMany(mappedBy = "protocol", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseInterrogationEducation> educations = new ArrayList<>();

    private String martialStatus;
    private String workOrStudyPlace;
    private String position;
    private String address;
    private String contactPhone;
    private String contactEmail;

    @Builder.Default
    @OneToMany(mappedBy = "protocol", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseInterrogationMilitaryRecord> militaries = new ArrayList<>();

    private String other;

    @Builder.Default
    @OneToMany(mappedBy = "protocol", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseInterrogationRelationRecord> relationRecords = new ArrayList<>();

    private String technical;

    @Builder.Default
    @OneToMany(mappedBy = "protocol", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseInterrogationCriminalRecord> criminals = new ArrayList<>();

    private String iinOrPassport;


    @OneToOne(mappedBy = "protocol")
    private CaseInterrogation interrogation;
}
