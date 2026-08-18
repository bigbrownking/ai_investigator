package org.di.digital.model.cases;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;

@Getter
@Setter
@Embeddable
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class CasePermission {

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false)
    private CaseModule module;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private CaseAction action;
}