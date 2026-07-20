package org.di.digital.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndictmentMigrationService {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public int migrateExistingIndictments() {
        int inserted = em.createNativeQuery("""
                INSERT INTO case_indictments (case_id, content, sections, generated_at, final_done)
                SELECT c.id, c.indictment, c.indictment_sections, c.indictment_generated_at, c.is_final_indictment_done
                FROM cases c
                WHERE (c.indictment IS NOT NULL OR c.indictment_sections IS NOT NULL)
                  AND NOT EXISTS (
                      SELECT 1 FROM case_indictments i WHERE i.case_id = c.id
                  )
                """).executeUpdate();

        log.info("Migrated {} indictments into case_indictments", inserted);
        return inserted;
    }
}