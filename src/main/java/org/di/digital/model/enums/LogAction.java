package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum LogAction {
    LOGIN("User Login"),
    SIGNUP("User Signup"),
    CASE_CREATED("Case Created"),
    CASE_STATUS_CHANGED("Case Status Changed"),
    CASE_DELETED("Case Deleted"),
    FILE_UPLOAD("File Upload"),
    FILE_DELETE("File Delete"),
    USER_ADD("User Added"),
    USER_DELETE("User Deleted"),
    CHAT_MESSAGE("Chat Message"),
    CHAT_CLEAR("Chat Clear"),
    FILE_DOWNLOAD("File Download"),
    QUALIFICATION("Qualification"),
    INDICTMENT("Indictment"),
    INDICTMENT_FINAL("Indictment Final"),
    PLAN_GENERATION("Plan Generation"),
    QUALIFICATION_DOWNLOAD("Qualification Download"),
    INDICTMENT_DOWNLOAD("Indictment Download"),
    INTERROGATION_DOWNLOAD("Interrogation Download"),
    INTERROGATION_ADDED("Interrogation Added"),
    INTERROGATION_DELETED("Interrogation Deleted"),
    FIGURANT_ADDED("Figurant Added"),
    FIGURANT_DELETED("Figurant Added"),
    MESSAGE_SELECTED("Message Selected"),
    CASE_UPDATED("Case Updated"),
    AUDIO_UPLOADED("Audio Uploaded"),
    INTERROGATION_COMPLETED("Interrogation Completed"),
    NO_FILE_PROCESSED("No File Processed"),
    NO_INTERROGATION_CLOSED("No Interrogation Closed"),
    NO_QUALIFICATION("No Qualification"),
    NO_SUCH_FILE("No Such File"),
    NO_ACCESS("No Access");

    private final String description;

    LogAction(String description) {
        this.description = description;
    }
}
