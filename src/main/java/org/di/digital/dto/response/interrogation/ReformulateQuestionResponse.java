package org.di.digital.dto.response.interrogation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ReformulateQuestionResponse {

    @JsonProperty("original_question")
    private String originalQuestion;

    private List<SuggestionDto> suggestions;

    private List<String> warnings;
}
