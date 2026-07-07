package org.di.digital.util.requests;

import lombok.experimental.UtilityClass;
import org.di.digital.model.enums.TreeModuleType;

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
    public static String indictmentSectionUrl(String host, String port) {
        return buildUrl(host, port, "/generate_akt_section");
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
    public static String osmotrDecisionUrl(String host, String port) {
        return buildUrl(host, port, "/api/submit-decisions");
    }
    public static String osmotrDownloadUrl(String host, String port, String sessionId, String fileType) {
        return buildUrl(host, port, String.format("/api/download/%s/%s", sessionId, fileType));
    }

    public static String interrogationReformulateUrl(String host, String port) {
        return buildUrl(host, port, "/api/interrogation/question/reformulate");
    }
    public static String interrogationCleanTranscriptUrl(String host, String port) {
        return buildUrl(host, port, "/api/interrogation/transcript/clean");
    }
    public static String analyticsQualification(String host, String port, String caseNumber){
        return buildUrl(host, port, String.format("/qualification/%s/analytics", caseNumber));
    }
    //TODO 9622 port

    public static String planUpdateUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, String.format("/api/plan/%s", caseNumber));
    }

    public static String buildModuleUrl(String host, String port, String caseNumber, TreeModuleType moduleType) {
        String endpoint = moduleType.getEndpoint(caseNumber);
        return String.format(HTTP_PROTOCOL + "%s:%s%s", host, port, endpoint);
    }
}