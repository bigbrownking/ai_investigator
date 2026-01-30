package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum LogAction {
    LOGIN("User Login"),
    CASE_CREATED("Case Created"),
    CASE_STATUS_CHANGED("Case Status Changed"),
    FILE_UPLOAD("File Upload"),
    CHAT_MESSAGE("Chat Message"),
    QUALIFICATION_DOWNLOAD("Qualification Download"),
    INDICTMENT_DOWNLOAD("Indictment Download");

    private final String description;

    LogAction(String description) {
        this.description = description;
    }
}
