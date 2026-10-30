package org.di.digital.reporting.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportingSchemaInitializerTest {

    private JdbcTemplate jdbcTemplate;
    private PlatformTransactionManager transactionManager;
    private ReportingSchemaInitializer initializer;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        initializer = new ReportingSchemaInitializer(jdbcTemplate, transactionManager);
    }

    @Test
    void legacySchemaRowsAreDeletedAndOldStructuresDropped() {
        legacyMarkerPresent(true);

        initializer.run(null);

        verify(jdbcTemplate).update("DELETE FROM reporting_submissions");
        verify(jdbcTemplate).update("DELETE FROM reporting_templates");
        ReportingSchemaInitializer.LEGACY_CLEANUP_DDL.forEach(ddl -> verify(jdbcTemplate).execute(ddl));
        verify(transactionManager).commit(any());
    }

    @Test
    void currentSchemaKeepsDataAndOnlyRunsIdempotentCleanup() {
        legacyMarkerPresent(false);

        initializer.run(null);

        verify(jdbcTemplate, never()).update(anyString());
        ReportingSchemaInitializer.LEGACY_CLEANUP_DDL.forEach(ddl -> verify(jdbcTemplate).execute(ddl));
    }

    @Test
    void partialUniqueIndexesAreEnsuredAfterCleanup() {
        legacyMarkerPresent(false);

        initializer.run(null);

        verify(jdbcTemplate).execute(startsWith("CREATE UNIQUE INDEX IF NOT EXISTS uq_reporting_templates_active"));
        verify(jdbcTemplate).execute(startsWith("CREATE UNIQUE INDEX IF NOT EXISTS uq_reporting_templates_draft"));
    }

    @Test
    void databaseErrorsAreLoggedNotThrown() {
        legacyMarkerPresent(true);
        doThrow(new DataAccessResourceFailureException("db down")).when(jdbcTemplate).update(anyString());
        doThrow(new DataAccessResourceFailureException("db down")).when(jdbcTemplate).execute(anyString());

        assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
        verify(transactionManager).rollback(any());
    }

    private void legacyMarkerPresent(boolean present) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("reporting_templates"), eq("row_definitions")))
                .thenReturn(present ? 1 : 0);
    }
}
