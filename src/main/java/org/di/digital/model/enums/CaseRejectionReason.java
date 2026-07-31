package org.di.digital.model.enums;

public enum CaseRejectionReason {
    DEADLINE_INTERRUPTED("Прерванные сроки"),
    TERMINATED("Прекращенные"),
    SENT_TO_COURT("Направленные в суд");

    private final String label;
    CaseRejectionReason(String label) { this.label = label; }
    public String getLabel() { return label; }
}