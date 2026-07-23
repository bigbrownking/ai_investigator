package org.di.digital.util.requests;

import lombok.experimental.UtilityClass;
import org.di.digital.model.enums.TreeModuleType;

@UtilityClass
public class RequestUrlBuilder {

    private static final String HTTP_PROTOCOL = "http://";

    public static String buildUrl(String host, String port, String endpoint) {
        return String.format(HTTP_PROTOCOL + "%s:%s%s", host, port, endpoint);
    }
    public static String deleteDocumentUrl(String host, String port, String caseNumber, String fileName) {
        return buildUrl(host, port, String.format("/documents/delete_document/%s/%s", caseNumber, fileName));
    }

    public static String deleteAllDocumentsUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, String.format("/documents/delete/%s", caseNumber));
    }
    public static String qualificationUrl(String host, String port, long userId, String caseNumber, String language) {
        return buildUrl(host, port,
                String.format("/workspaces/%s/generate-qualification?user_id=%d&mode=hybrid&language=%s",
                        caseNumber, userId, language));
    }
    public static String qualificationSectionUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, String.format("/qualification/%s/regenerate-qualification?mode=hybrid", caseNumber));
    }
    public static String qualificationPromptUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, String.format("/qualification/%s/rephrase", caseNumber));
    }
    public static String caseInfoUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, String.format("/workspaces/%s/case-info", caseNumber));
    }

    public static String qualificationChatUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, "/query/" + caseNumber);
    }

    public static String indictmentUrl(String host, String port) {
        return buildUrl(host, port, "/generate_akt");
    }
    public static String indictmentSectionUrl(String host, String port) {
        return buildUrl(host, port, "/generate_akt_section");
    }
    public static String indictmentPromptUrl(String host, String port) {
        return buildUrl(host, port, "/change_context");
    }

    public static String interrogationQuestionsUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, String.format("/interrogation/questions/%s", caseNumber));
    }

    public static String interrogationContradictionUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, String.format("/interrogation/%s/contradiction", caseNumber));
    }

    public static String generalChatUrl(String host, String port) {
        return buildUrl(host, port, "/chat");
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
        return buildUrl(host, port, String.format("/workspaces/%s/figurants", caseNumber));
    }
    public static String osmotrDecisionUrl(String host, String port) {
        return buildUrl(host, port, "/api/submit-decisions");
    }
    public static String osmotrDownloadUrl(String host, String port, String sessionId, String fileType) {
        return buildUrl(host, port, String.format("/api/download/%s/%s", sessionId, fileType));
    }

    public static String interrogationReformulateUrl(String host, String port) {
        return buildUrl(host, port, "/interrogation/question/reformulate");
    }
    public static String interrogationCleanTranscriptUrl(String host, String port) {
        return buildUrl(host, port, "/interrogation/transcript/clean");
    }
    public static String analyticsQualification(String host, String port, String caseNumber, String language){
        return buildUrl(host, port, String.format("/qualification/%s/analytics?language=%s", caseNumber, language));
    }

    public static String planUpdateUrl(String host, String port, String caseNumber) {
        return buildUrl(host, port, String.format("/api/plan/%s", caseNumber));
    }

    public static String buildModuleUrl(String host, String port, String caseNumber, TreeModuleType moduleType) {
        String endpoint = moduleType.getEndpoint(caseNumber);
        return String.format(HTTP_PROTOCOL + "%s:%s%s", host, port, endpoint);
    }
}