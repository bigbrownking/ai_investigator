package org.di.digital.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.queue.TaskQueue;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.repository.user.UserRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileOwnerMigrationService {

    private final CaseFileRepository caseFileRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    @Transactional
    public FileOwnerMigrationResult migrateFileOwners() {
        List<CaseFile> files = caseFileRepository.findAll();

        int updatedFromTask = 0;
        int updatedFromOwner = 0;
        int skippedHasOwner = 0;
        int unresolved = 0;

        Map<String, User> userCache = new HashMap<>();

        for (CaseFile file : files) {
            if (file.getUserEntity() != null) {
                skippedHasOwner++;
                continue;
            }

            User resolved = resolveFromTask(file, userCache);
            if (resolved != null) {
                file.setUserEntity(resolved);
                updatedFromTask++;
                log.info("File {} (id {}) owner set to {} from TaskQueue",
                        file.getOriginalFileName(), file.getId(), resolved.getEmail());
                continue;
            }

            User caseOwner = resolveFromCaseOwner(file);
            if (caseOwner != null) {
                file.setUserEntity(caseOwner);
                updatedFromOwner++;
                log.info("File {} (id {}) owner set to {} from case owner (fallback)",
                        file.getOriginalFileName(), file.getId(), caseOwner.getEmail());
                continue;
            }

            log.warn("Could not resolve owner for file {} (id {}), leaving unset",
                    file.getOriginalFileName(), file.getId());
            unresolved++;
        }

        caseFileRepository.saveAll(files);

        FileOwnerMigrationResult result = new FileOwnerMigrationResult(
                files.size(), updatedFromTask, updatedFromOwner, skippedHasOwner, unresolved);

        log.info("File owner migration done. {}", result);
        return result;
    }

    private User resolveFromTask(CaseFile file, Map<String, User> userCache) {
        String email = findEmailByCaseFileId(file.getId());
        if (email == null) {
            return null;
        }
        return userCache.computeIfAbsent(email,
                e -> userRepository.findByEmail(e).orElse(null));
    }

    private User resolveFromCaseOwner(CaseFile file) {
        Case caseEntity = file.getCaseEntity();
        if (caseEntity == null) {
            return null;
        }
        return caseEntity.getOwner();
    }

    private String findEmailByCaseFileId(Long caseFileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("caseFileId").is(caseFileId));
        TaskQueue task = mongoTemplate.findOne(query, TaskQueue.class);
        return task != null ? task.getUserEmail() : null;
    }

    public record FileOwnerMigrationResult(
            int totalFiles,
            int updatedFromTask,
            int updatedFromOwner,
            int skippedHasOwner,
            int unresolved) {

        public int totalUpdated() {
            return updatedFromTask + updatedFromOwner;
        }
    }
}