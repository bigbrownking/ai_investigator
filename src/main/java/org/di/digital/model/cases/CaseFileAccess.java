package org.di.digital.model.cases;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.user.User;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "case_file_access",
        uniqueConstraints = @UniqueConstraint(columnNames = {"file_id", "user_id"}))
public class CaseFileAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id")
    private CaseFile file;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "case_file_access_actions",
            joinColumns = @JoinColumn(name = "file_access_id"))
    @Column(name = "action")
    @Enumerated(EnumType.STRING)
    private Set<CaseAction> actions = new HashSet<>();
}