package org.di.digital.model.enums;

public enum CaseActivityType {
    CASE_CREATED("Case created"),
    FILE_UPLOADED("File uploaded"),
    FILE_DELETED("File deleted"),
    CHAT_MESSAGE("Chat message sent"),
    QUALIFICATION_GENERATED("Qualification generated"),
    INDICTMENT_GENERATED("Indictment generated"),
    INTERROGATION_ADDED("Interrogation added"),
    USER_ADDED("User added to case"),
    USER_REMOVED("User removed from case"),
    STATUS_CHANGED("Case status changed");

    private final String description;

    CaseActivityType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}