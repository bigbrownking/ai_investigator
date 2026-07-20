package org.di.digital.dto.response.interrogation;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class InterrogationQuestionsResponse {
    private String fio;
    private String role;
    private String language;
    private List<String> questions;
}