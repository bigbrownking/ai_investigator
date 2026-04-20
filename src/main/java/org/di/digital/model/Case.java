package org.di.digital.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.model.enums.CaseInterrogationStatusEnum;
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

    @Builder.Default
    private boolean status = true;

    @Builder.Default
    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<CaseFile> files = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseInterrogation> interrogations = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Figurant> figurants = new ArrayList<>();

    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseChat> chats = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String qualification;

    @Column(columnDefinition = "TEXT")
    private String indictment;

    private Boolean isFinalIndictmentDone;
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

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

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

    public List<String> getQualificationsUploaded(){
        List<String> qualificationNames = new ArrayList<>();
        for(CaseFile caseFile : files){
            if(caseFile.isQualification()){
                qualificationNames.add(caseFile.getOriginalFileName());
            }
        }
        return qualificationNames;
    }
    public void removeInterrogation(CaseInterrogation interrogation) {
        this.interrogations.remove(interrogation);
        interrogation.setCaseEntity(null);
    }
    public void addFigurant(Figurant figurant) {
        this.figurants.add(figurant);
        figurant.setCaseEntity(this);
    }

    public void removeFigurant(Figurant figurant) {
        this.figurants.remove(figurant);
        figurant.setCaseEntity(null);
    }

    public void removeAllAttachedFiles() {
        this.files.clear();

        for (CaseInterrogation interrogation : this.interrogations) {

            if (interrogation.getApplicationFiles() != null) {
                interrogation.getApplicationFiles().clear();
            }

            if (interrogation.getOtherAudios() != null) {
                interrogation.getOtherAudios().clear();
            }
        }
    }
    public boolean isAtLeastOneFileProcessed() {
        return files.stream()
                .anyMatch(f -> CaseFileStatusEnum.COMPLETED.equals(f.getStatus()));
    }
}
