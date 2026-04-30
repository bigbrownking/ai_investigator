package org.di.digital.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "case_interrogation_militaries")
public class CaseInterrogationMilitaryRecord {
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    private String type;
    private String about;

    @ManyToOne
    @JoinColumn(name = "protocol_id")
    private CaseInterrogationProtocol protocol;
}
