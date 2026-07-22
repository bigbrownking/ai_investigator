package org.di.digital.model.cases;

import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.model.enums.PlanStatus;
import org.di.digital.model.indictment.CaseIndictment;
import org.di.digital.model.interrogation.CaseFigurant;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.plan.CasePlan;
import org.di.digital.model.plan.PlanApprovalHistory;
import org.di.digital.model.qualification.CaseQualification;
import org.di.digital.model.user.User;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "cases")
@EqualsAndHashCode(of = "number")
@EntityListeners(AuditingEntityListener.class)
public class Case {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String number;

    private String language;

    @Builder.Default
    private boolean status = true;

    @OneToOne(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private CasePlan casePlan;

    @OneToOne(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private CaseQualification caseQualification;

    @OneToOne(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private CaseIndictment caseIndictment;

    @Builder.Default
    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<CaseFile> files = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseInterrogation> interrogations = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseFigurant> figurants = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseChat> chats = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanApprovalHistory> planApprovalHistories = new ArrayList<>();

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

    public List<String> getQualificationsUploaded() {
        List<String> qualificationNames = new ArrayList<>();
        for (CaseFile caseFile : files) {
            if (caseFile.isQualification()) {
                qualificationNames.add(caseFile.getOriginalFileName());
            }
        }
        return qualificationNames;
    }

    public void removeInterrogation(CaseInterrogation interrogation) {
        this.interrogations.remove(interrogation);
        interrogation.setCaseEntity(null);
    }

    public void addFigurant(CaseFigurant figurant) {
        this.figurants.add(figurant);
        figurant.setCaseEntity(this);
    }

    public void removeFigurant(CaseFigurant figurant) {
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

    public long audioUsedCount() {
        return interrogations.stream()
                .filter(CaseInterrogation::isAudioUsed)
                .count();
    }

    public String getQualification() {
        return caseQualification != null ? caseQualification.getContent() : null;
    }

    public LocalDateTime getQualificationGeneratedAt() {
        return caseQualification != null ? caseQualification.getGeneratedAt() : null;
    }

    public boolean hasQualification() {
        return caseQualification != null && caseQualification.getContent() != null;
    }
    public boolean hasIndictment() {
        return getIndictment() != null || getIndictmentSections() != null;
    }

    public String getIndictment() {
        return caseIndictment != null ? caseIndictment.getContent() : null;
    }

    public List<Map<String, Object>> getIndictmentSections() {
        return caseIndictment != null ? caseIndictment.getSections() : null;
    }
    public List<Map<String, Object>> getQualificationSections() {
        return caseQualification != null ? caseQualification.getSections() : null;
    }

    public LocalDateTime getIndictmentGeneratedAt() {
        return caseIndictment != null ? caseIndictment.getGeneratedAt() : null;
    }

    public Boolean getIsFinalIndictmentDone() {
        return caseIndictment != null ? caseIndictment.getFinalDone() : null;
    }

    public Map<String, Object> getPlan() {
        return casePlan != null ? casePlan.getContent() : null;
    }
    public PlanStatus getPlanStatus() {
        return casePlan != null ? casePlan.getStatus() : null;
    }
    public String getPlanReviewComment() {
        return casePlan != null ? casePlan.getReviewComment() : null;
    }
    public User getPlanReviewedBy() {
        return casePlan != null ? casePlan.getReviewedBy() : null;
    }
    public User getPlanApprovedBy() {
        return casePlan != null ? casePlan.getApprovedBy() : null;
    }
    public User getPlanSubmittedBy() {
        return casePlan != null ? casePlan.getSubmittedBy() : null;
    }
    public LocalDateTime getPlanGeneratedAt() {
        return casePlan != null ? casePlan.getGeneratedAt() : null;
    }
    public LocalDateTime getPlanReviewedAt() {
        return casePlan != null ? casePlan.getReviewedAt() : null;
    }
    public LocalDateTime getPlanSubmittedAt() {
        return casePlan != null ? casePlan.getSubmittedAt() : null;
    }
    public LocalDateTime getPlanAgreedAt() {
        return casePlan != null ? casePlan.getAgreedAt() : null;
    }
    public LocalDateTime getPlanApprovedAt() {
        return casePlan != null ? casePlan.getApprovedAt() : null;
    }
    public boolean hasPlan() {
        return getPlan() != null;
    }
    public boolean hasBothQualifications() {
        boolean hasHumanQualificationFile = files.stream()
                .anyMatch(CaseFile::isQualification);
        return hasQualification() && hasHumanQualificationFile;
    }

    public Set<Integer> getNotifiedRedActions() {
        return casePlan != null ? casePlan.getNotifiedRedActions() : null;
    }

    private CasePlan ensurePlan() {
        if (casePlan == null) {
            casePlan = CasePlan.builder().caseEntity(this).build();
        }
        return casePlan;
    }
    public void setNotifiedRedActions(Set<Integer> notifiedRedActions) {
        ensurePlan().setNotifiedRedActions(notifiedRedActions);
    }

    public void setPlan(Map<String, Object> plan) {
        ensurePlan().setContent(plan);
    }
    public void setPlanStatus(PlanStatus status) {
        ensurePlan().setStatus(status);
    }
    public void setPlanReviewComment(String comment) {
        ensurePlan().setReviewComment(comment);
    }
    public void setPlanReviewedBy(User user) {
        ensurePlan().setReviewedBy(user);
    }
    public void setPlanApprovedBy(User user) {
        ensurePlan().setApprovedBy(user);
    }
    public void setPlanSubmittedBy(User user) {
        ensurePlan().setSubmittedBy(user);
    }
    public void setPlanGeneratedAt(LocalDateTime dt) {
        ensurePlan().setGeneratedAt(dt);
    }
    public void setPlanReviewedAt(LocalDateTime dt) {
        ensurePlan().setReviewedAt(dt);
    }
    public void setPlanSubmittedAt(LocalDateTime dt) {
        ensurePlan().setSubmittedAt(dt);
    }
    public void setPlanAgreedAt(LocalDateTime dt) {
        ensurePlan().setAgreedAt(dt);
    }
    public void setPlanApprovedAt(LocalDateTime dt) {
        ensurePlan().setApprovedAt(dt);
    }
}
