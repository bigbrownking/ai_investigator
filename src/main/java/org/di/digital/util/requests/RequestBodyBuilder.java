package org.di.digital.util.requests;

import lombok.experimental.UtilityClass;
import org.di.digital.dto.request.interrogation.CleanTranscriptRequest;
import org.di.digital.dto.request.interrogation.ReformulateQuestionRequest;
import org.di.digital.model.interrogation.CaseInterrogationQA;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UtilityClass
public class RequestBodyBuilder {

    public static Map<String, Object> generalChatBody(String question) {
        Map<String, Object> body = new HashMap<>();
        body.put("query", question);
        return body;
    }

    public static Map<String, Object> indictmentBody(String caseNumber, List<String> qualoNames,
                                                     long userId, boolean isDone, String language) {
        Map<String, Object> body = new HashMap<>();
        body.put("working_dir", caseNumber);
        body.put("mode", "hybrid");
        body.put("qual_name", qualoNames);
        body.put("stream", false);
        body.put("user_id", userId);
        body.put("is_done", isDone);
        body.put("language", language);
        return body;
    }

    public static Map<String, Object> indictmentSectionBody(String caseNumber, List<String> qualoNames,
                                                     long userId, boolean isDone, String language, int id) {
        Map<String, Object> body = new HashMap<>();
        body.put("working_dir", caseNumber);
        body.put("mode", "hybrid");
        body.put("qual_name", qualoNames);
        body.put("stream", false);
        body.put("user_id", userId);
        body.put("is_done", isDone);
        body.put("language", language);
        body.put("id", id);
        return body;
    }

    public static Map<String, Object> indictmentPromptBody(String caseNumber, String context, String prompt, String language) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_wants", prompt);
        body.put("context", context);
        body.put("language", language);
        body.put("working_dir", caseNumber);
        return body;
    }


    public static Map<String, Object> interrogationBody(String fio, String role,
                                                        String language, List<CaseInterrogationQA> qaList) {
        String prior_qa_text = qaList == null || qaList.isEmpty() ? "" :
                qaList.stream()
                        .map(qa -> "Вопрос: " + qa.getQuestion() + " Ответ: " + qa.getAnswer())
                        .collect(Collectors.joining("\n"));
        Map<String, Object> body = new HashMap<>();
        body.put("fio", fio);
        body.put("role", role);
        body.put("language", language);
        body.put("prior_qa_text", prior_qa_text);
        return body;
    }

    //mode: initial/append/update
    public static MultiValueMap<String, Object> planBody(String caseNumber, String mode) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("case_number", caseNumber);
        body.add("mode", mode);
        body.add("response_format", "json");
        return body;
    }

    public static Map<String, Object> manualStatusBody(int actionNumber, String newStatus, String role, String comment) {
        Map<String, Object> body = new HashMap<>();
        body.put("action_number", actionNumber);
        body.put("new_status", newStatus);
        if (role != null && !role.isBlank()) {
            body.put("role", role);
        }
        if (comment != null && !comment.isBlank()) {
            body.put("comment", comment);
        }
        return body;
    }

    public static Map<String, Object> interrogationReformulateQuestionBody(ReformulateQuestionRequest request) {
        String language =  request.getLanguage().equals("русском") ? "russian" : "kazakh";
        Map<String, Object> body = new HashMap<>();

        body.put("case_id", request.getCaseNumber());
        body.put("person_role", request.getPersonRole());
        body.put("question", request.getQuestion());
        body.put("language", language);
        body.put("style", request.getStyle());
        body.put("max_variants", request.getMaxVariants());

        return body;
    }

    public static Map<String, Object> cleanTranscriptBody(CleanTranscriptRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("text", request.getText());
        body.put("language", request.getLanguage());
        return body;
    }
}