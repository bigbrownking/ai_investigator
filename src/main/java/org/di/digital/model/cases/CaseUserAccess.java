package org.di.digital.model.cases;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;
import org.di.digital.model.enums.permission.DocumentAccessScope;
import org.di.digital.model.user.User;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "case_user_access",
        uniqueConstraints = @UniqueConstraint(columnNames = {"case_id", "user_id"}))
public class CaseUserAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id")
    private Case caseEntity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "case_user_permissions",
            joinColumns = @JoinColumn(name = "access_id"),
            uniqueConstraints = @UniqueConstraint(
                    columnNames = {"access_id", "module", "action"}))
    private Set<CasePermission> permissions = new HashSet<>();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "document_scope", nullable = false)
    private DocumentAccessScope documentScope = DocumentAccessScope.ALL;

    public boolean can(CaseModule module, CaseAction action) {
        return permissions.stream()
                .anyMatch(p -> p.getModule() == module && p.getAction() == action);
    }

    public boolean canAccessModule(CaseModule module) {
        return permissions.stream().anyMatch(p -> p.getModule() == module);
    }
}