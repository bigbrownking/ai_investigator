package org.di.digital.reporting.model.enums;

/**
 * State of one region's row for a form and a date, as shown to operators and in the consolidated table.
 */
public enum ReportRowStatus {
    /** Nothing saved for this date yet. */
    NOT_SUBMITTED,
    /** Draft saved, not submitted: the consolidated table shows empty cells. */
    DRAFT,
    SUBMITTED
}
