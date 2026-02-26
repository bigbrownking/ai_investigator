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
    private String education;
    private String martialStatus;
    private String workOrStudyPlace;
    private String position;
    private String address;
    private String contacts;
    private String military;
    private String criminalRecord;
    private String iinOrPassport;


    @OneToOne(mappedBy = "protocol")
    private CaseInterrogation interrogation;
}
