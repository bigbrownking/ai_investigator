package org.di.digital.util;

import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UtilityClass
public class RequestBodyBuilder {

    public static Map<String, Object> generalChatBody(String question) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", "qwen3-next-80b-instruct");
        body.put("messages", java.util.List.of(
                Map.of("role", "user", "content", question)
        ));
        body.put("max_tokens", 16324);
        body.put("temperature", 0.2);
        body.put("stream", true);
        return body;
    }

    public static Map<String, Object> indictmentBody(String caseNumber, List<String> qualoNames, long userId) {
        Map<String, Object> body = new HashMap<>();
        body.put("working_dir", caseNumber);
        body.put("mode", "hybrid");
        body.put("qual_name", qualoNames);
        body.put("stream", false);
       // body.put("user_id", userId);
        return body;
    }


    public static Map<String, Object> interrogationBody(String fio, String role, String question, String answer) {
        String lastQuestion = String.format("Вопрос: %s\\nОтвет: %s", question, answer);
        Map<String, Object> body = new HashMap<>();
        body.put("fio", fio);
        body.put("role", role);
        body.put("last_question", lastQuestion);
        return body;
    }
}