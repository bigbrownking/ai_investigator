package org.di.digital.util.requests;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RequestUrlBuilder {

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
        return buildUrl(host, port, "/chat/stream");
    }

    public static String planGeneratorUrl(String host, String port) {
        return buildUrl(host, port, "/api/generate_plan");
    }
    public static String manualStatusUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, String.format("/api/manual_status/%s", caseNumber));
    }
    public static String renameWorkspaceUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, String.format("/workspaces/%s/rename", caseNumber));
    }
    public static String figurantUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, String.format("/workspaces/%s/figurants?include_references=true", caseNumber));
    }
    public static String osmotrUploadUrl(String host, String port) {
        return buildUrl(host, port, "/api/upload");
    }
    public static String osmotrUpReportUrl(String host, String port) {
        return buildUrl(host, port, "/api/generate-report");
    }
    public static String osmotrDecisionUrl(String host, String port) {
        return buildUrl(host, port, "/api/submit-decisions");
    }
    public static String osmotrDownloadUrl(String host, String port, String fileType, String sessionId) {
        return buildUrl(host, port, String.format("/api/download/%s?session_id=%s", fileType, sessionId));
    }

    public static String interrogationReformulateUrl(String host, String port) {
        return buildUrl(host, port, "/api/interrogation/question/reformulate");
    }
    public static String interrogationCleanTranscriptUrl(String host, String port) {
        return buildUrl(host, port, "/api/interrogation/transcript/clean");
    }

    public static String planUpdateUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, String.format("/api/plan/%s", caseNumber));
    }
}