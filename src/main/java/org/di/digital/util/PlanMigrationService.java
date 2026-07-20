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
public class PlanMigrationService {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public int migrateExistingPlans() {
        int inserted = em.createNativeQuery("""
                INSERT INTO case_plans (
                    case_id, content, status, review_comment,
                    plan_reviewed_by_id, plan_approved_by_id, plan_submitted_by_id,
                    notified_red_actions,
                    generated_at, reviewed_at, submitted_at, agreed_at, approved_at
                )
                SELECT c.id, c.plan, c.plan_status, c.plan_review_comment,
                       c.plan_reviewed_by_id, c.plan_approved_by_id, c.plan_submitted_by_id,
                       c.notified_red_actions,
                       c.plan_generated_at, c.plan_reviewed_at, c.plan_submitted_at,
                       c.plan_agreed_at, c.plan_approved_at
                FROM cases c
                WHERE (c.plan IS NOT NULL)
                  AND NOT EXISTS (
                      SELECT 1 FROM case_plans p WHERE p.case_id = c.id
                  )
                """).executeUpdate();

        log.info("Migrated {} plans into case_plans", inserted);
        return inserted;
    }
}