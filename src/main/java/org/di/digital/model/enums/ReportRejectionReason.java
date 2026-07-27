package org.di.digital.model.enums;

public enum ReportRejectionReason{
    DEADLINE_INTERRUPTED("прерванные сроки"),
    TERMINATED("прекращенные"),
    SENT_TO_COURT("направленные в суд");

    private String label;

    ReportRejectionReason(String label){
        this.label = label;
    }

    public String getLabel(){
        return label;
    }
}