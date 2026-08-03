package org.di.digital.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.Log;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.user.User;
import org.di.digital.repository.LogRepository;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.repository.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterrogationOwnerMigrationService {

    private final CaseInterrogationRepository caseInterrogationRepository;
    private final LogRepository logRepository;
    private final UserRepository userRepository;

    @Transactional
    public InterrogationOwnerMigrationResult migrateInterrogationOwners() {
        List<CaseInterrogation> interrogations = caseInterrogationRepository.findAll();

        int updatedFromLog = 0;
        int updatedFromOwner = 0;
        int skippedHasOwner = 0;
        int unresolved = 0;

        Map<String, User> userCache = new HashMap<>();
        Map<String, List<Log>> logCache = new HashMap<>();

        for (CaseInterrogation interrogation : interrogations) {
            if (interrogation.getUserEntity() != null) {
                skippedHasOwner++;
                continue;
            }

            User resolved = resolveFromLog(interrogation, logCache, userCache);
            if (resolved != null) {
                interrogation.setUserEntity(resolved);
                updatedFromLog++;
                log.info("Interrogation {} (id {}) owner set to {} from log",
                        interrogation.getFio(), interrogation.getId(), resolved.getEmail());
                continue;
            }

            User caseOwner = resolveFromCaseOwner(interrogation);
            if (caseOwner != null) {
                interrogation.setUserEntity(caseOwner);
                updatedFromOwner++;
                log.info("Interrogation {} (id {}) owner set to {} from case owner (fallback)",
                        interrogation.getFio(), interrogation.getId(), caseOwner.getEmail());
                continue;
            }

            log.warn("Could not resolve owner for interrogation {} (id {}), leaving unset",
                    interrogation.getFio(), interrogation.getId());
            unresolved++;
        }

        caseInterrogationRepository.saveAll(interrogations);

        InterrogationOwnerMigrationResult result = new InterrogationOwnerMigrationResult(
                interrogations.size(), updatedFromLog, updatedFromOwner, skippedHasOwner, unresolved);

        log.info("Interrogation owner migration done. {}", result);
        return result;
    }

    private User resolveFromLog(CaseInterrogation interrogation,
                                Map<String, List<Log>> logCache,
                                Map<String, User> userCache) {
        Case caseEntity = interrogation.getCaseEntity();
        if (caseEntity == null || caseEntity.getNumber() == null) {
            return null;
        }
        String caseNumber = caseEntity.getNumber();
        String fioNorm = normalize(interrogation.getFio());
        if (fioNorm == null) {
            return null;
        }

        List<Log> logs = logCache.computeIfAbsent(caseNumber,
                cn -> logRepository.findByActionAndCaseNumber(LogAction.INTERROGATION_ADDED, cn));

        // логи этого дела, где нормализованное описание содержит нормализованное ФИО
        List<Log> matching = logs.stream()
                .filter(l -> l.getEmail() != null)
                .filter(l -> {
                    String descNorm = normalize(l.getDescription());
                    return descNorm != null && descNorm.contains(fioNorm);
                })
                .toList();

        if (matching.isEmpty()) {
            return null;
        }

        // Для доп. допросов ФИО совпадает — email в норме один и тот же (тот же создатель).
        // Тайбрейк по времени нужен лишь если у одного ФИО в деле логи с разными email.
        Log best = matching.get(0);
        boolean multipleEmails = matching.stream()
                .map(Log::getEmail)
                .distinct()
                .count() > 1;

        if (multipleEmails) {
            LocalDateTime anchor = interrogation.getCreatedDate() != null
                    ? interrogation.getCreatedDate()
                    : interrogation.getStartedAt();
            if (anchor != null) {
                final LocalDateTime a = anchor;
                best = matching.stream()
                        .filter(l -> l.getTimestamp() != null)
                        .min(Comparator.comparing(l -> Duration.between(l.getTimestamp(), a).abs()))
                        .orElse(best);
            }
        }

        String email = best.getEmail();
        return userCache.computeIfAbsent(email,
                e -> userRepository.findByEmail(e).orElse(null));
    }

    private User resolveFromCaseOwner(CaseInterrogation interrogation) {
        Case caseEntity = interrogation.getCaseEntity();
        return caseEntity != null ? caseEntity.getOwner() : null;
    }

    /** trim + схлопывание пробелов + нижний регистр; null-safe. */
    private String normalize(String s) {
        if (s == null) {
            return null;
        }
        String n = s.trim().replaceAll("\\s+", " ").toLowerCase();
        return n.isEmpty() ? null : n;
    }

    public record InterrogationOwnerMigrationResult(
            int totalInterrogations,
            int updatedFromLog,
            int updatedFromOwner,
            int skippedHasOwner,
            int unresolved) {

        public int totalUpdated() {
            return updatedFromLog + updatedFromOwner;
        }
    }
}