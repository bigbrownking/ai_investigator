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
    private String military;
    private String other;
    private String relation;
    private String technical;
    private String criminalRecord;
    private String iinOrPassport;


    @OneToOne(mappedBy = "protocol")
    private CaseInterrogation interrogation;
}
