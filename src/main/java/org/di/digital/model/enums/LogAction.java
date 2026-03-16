package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum LogAction {
    LOGIN("User Login"),
    CASE_CREATED("Case Created"),
    CASE_STATUS_CHANGED("Case Status Changed"),
    FILE_UPLOAD("File Upload"),
    FILE_DELETE("File Delete"),
    USER_ADD("User Added"),
    USER_DELETE("User Deleted"),
    CHAT_MESSAGE("Chat Message"),
    CHAT_CLEAR("Chat Clear"),
    FILE_DOWNLOAD("File Download"),
    QUALIFICATION_DOWNLOAD("Qualification Download"),
    INDICTMENT_DOWNLOAD("Indictment Download"),
    INTERROGATION_DOWNLOAD("Interrogation Download"),
    INTERROGATION_ADDED("Interrogation Added");

    private final String description;

    LogAction(String description) {
        this.description = description;
    }
}
