package org.di.digital.reporting.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Schema work that ddl-auto=update cannot do; runs after it (tables exist) and before the template seeder.
 * <ol>
 *   <li>Removes what earlier versions of the module left behind: ddl-auto never drops columns or tables.
 *       Rows of the first JPA version (templates with rows, nested cells) cannot be read by the current
 *       model, so when its marker column is present those rows are deleted as well.</li>
 *   <li>Creates partial unique indexes, which JPA annotations cannot declare.</li>
 * </ol>
 * Failures are logged, not thrown, so the rest of the application still starts.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class ReportingSchemaInitializer implements ApplicationRunner {

    /** Present only in the first JPA version of reporting_templates (rows + columns). */
    static final String LEGACY_MARKER_TABLE = "reporting_templates";
    static final String LEGACY_MARKER_COLUMN = "row_definitions";

    static final List<String> LEGACY_CLEANUP_DDL = List.of(
            "ALTER TABLE IF EXISTS reporting_templates DROP COLUMN IF EXISTS row_definitions",
            "ALTER TABLE IF EXISTS reporting_submissions"
                    + " DROP COLUMN IF EXISTS draft_cells,"
                    + " DROP COLUMN IF EXISTS draft_template_version,"
                    + " DROP COLUMN IF EXISTS draft_updated_by,"
                    + " DROP COLUMN IF EXISTS draft_updated_at,"
                    + " DROP COLUMN IF EXISTS template_id",
            "DROP INDEX IF EXISTS ix_reporting_submissions_code_date_status",
            "DROP TABLE IF EXISTS reporting_consolidated");

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void run(ApplicationArguments args) {
        cleanUpLegacySchema();
        // At most one ACTIVE and one DRAFT version per form
        createIndex("uq_reporting_templates_active",
                "CREATE UNIQUE INDEX IF NOT EXISTS uq_reporting_templates_active "
                        + "ON reporting_templates (code) WHERE status = 'ACTIVE'");
        createIndex("uq_reporting_templates_draft",
                "CREATE UNIQUE INDEX IF NOT EXISTS uq_reporting_templates_draft "
                        + "ON reporting_templates (code) WHERE status = 'DRAFT'");
    }

    void cleanUpLegacySchema() {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                if (columnExists(LEGACY_MARKER_TABLE, LEGACY_MARKER_COLUMN)) {
                    int submissions = jdbcTemplate.update("DELETE FROM reporting_submissions");
                    int templates = jdbcTemplate.update("DELETE FROM reporting_templates");
                    log.warn("Reporting: legacy schema found, deleted {} templates and {} submissions "
                            + "that the current model cannot read; templates are re-seeded", templates, submissions);
                }
                LEGACY_CLEANUP_DDL.forEach(jdbcTemplate::execute);
            });
            log.info("Reporting legacy schema cleanup done");
        } catch (DataAccessException | TransactionException e) {
            log.error("Reporting legacy schema cleanup failed; old columns or rows may break reporting", e);
        }
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    private void createIndex(String name, String ddl) {
        try {
            jdbcTemplate.execute(ddl);
            log.info("Reporting index {} ensured", name);
        } catch (DataAccessException e) {
            log.error("Reporting index {} could not be created; check reporting_templates for duplicate rows", name, e);
        }
    }
}
