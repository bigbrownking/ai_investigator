package org.di.digital.reporting.integration;

/**
 * Current user as seen by the reporting module: plain ids only.
 * User/Region entities are touched solely by the implementation (SecurityReportingUserContext).
 */
public interface ReportingUserContext {

    Long currentOperatorId();

    /** Region the current operator reports for; operators may write only to this region. */
    Long currentRegionId();
}
