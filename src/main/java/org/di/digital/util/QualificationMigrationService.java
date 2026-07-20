package org.di.digital.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualificationMigrationService {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public int migrateExistingQualifications() {
        int inserted = em.createNativeQuery("""
                INSERT INTO case_qualifications (case_id, content, generated_at)
                SELECT c.id, c.qualification, c.qualification_generated_at
                FROM cases c
                WHERE c.qualification IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1 FROM case_qualifications q WHERE q.case_id = c.id
                  )
                """).executeUpdate();

        log.info("Migrated {} qualifications into case_qualifications", inserted);
        return inserted;
    }
}