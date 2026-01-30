package org.di.digital.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "cases")
@EntityListeners(AuditingEntityListener.class)
public class Case {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String number;

    private String description;

    @Builder.Default
    private boolean status = true;

    @Builder.Default
    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseFile> files = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseInterrogation> interrogations = new ArrayList<>();

    @OneToOne(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private CaseChat chat;

    @Column(columnDefinition = "TEXT")
    private String qualification;

    @Column(columnDefinition = "TEXT")
    private String indictment;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "case_users",
            joinColumns = @JoinColumn(name = "case_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> users = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    //    @LastModifiedDate
//    @Column(name = "updated_date")
//    private LocalDateTime updatedDate;

    @Column(name = "last_activity_date")
    private LocalDateTime lastActivityDate;

    @Column(name = "last_activity_type", length = 50)
    private String lastActivityType;

    @Column(name = "qualification_generated_at")
    private LocalDateTime qualificationGeneratedAt;

    @Column(name = "indictment_generated_at")
    private LocalDateTime indictmentGeneratedAt;

    public void addUser(User user) {
        this.users.add(user);
        user.getCases().add(this);
        log.debug("Added user {} to case {}", user.getEmail(), this.number);
    }

    public void removeUser(User user) {
        this.users.remove(user);
        user.getCases().remove(this);
        log.debug("Removed user {} from case {}", user.getEmail(), this.number);
    }

    public boolean hasUser(User user) {
        return this.users.contains(user);
    }

    public boolean isOwner(User user) {
        return this.owner != null && this.owner.equals(user);
    }
    public void updateActivity(String activityType) {
        this.lastActivityDate = LocalDateTime.now();
        this.lastActivityType = activityType;
    }
}
