package org.di.digital.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class UrlBuilder {

    private static final String HTTP_PROTOCOL = "http://";

    public static String buildUrl(String host, String port, String endpoint) {
        return String.format(HTTP_PROTOCOL + "%s:%s%s", host, port, endpoint);
    }

    public static String qualificationUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port,
                String.format("/workspaces/%s/generate-qualification?mode=hybrid&stream=false", caseNumber));
    }
    public static String caseInfoUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, String.format("/workspaces/%s/case-info", caseNumber));
    }

    public static String qualificationChatUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, "/chat/" + caseNumber);
    }

    public static String indictmentUrl(String host, String port) {
        return buildUrl(host, port, "/generate_akt");
    }

    public static String interrogationQuestionsUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, String.format("/interrogation/questions/%s", caseNumber));
    }

    public static String generalChatUrl(String host, String port) {
        return buildUrl(host, port, "/v1/chat/completions");
    }

    public static String planGeneratorUrl(String host, String port) {
        return buildUrl(host, port, "/api/generate_plan");
    }
    public static String renameWorkspaceUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, String.format("/workspaces/%s/rename", caseNumber));
    }
}