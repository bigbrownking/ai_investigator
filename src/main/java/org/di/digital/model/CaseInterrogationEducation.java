package org.di.digital.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "case_interrogation_educations")
public class CaseInterrogationEducation {
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    private String type;
    private String edu;

    @ManyToOne
    @JoinColumn(name = "protocol_id")
    private CaseInterrogationProtocol protocol;
}
