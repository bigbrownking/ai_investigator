package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.request.interrogation.ReformulateQuestionRequest;
import org.di.digital.dto.response.interrogation.ReformulateQuestionResponse;
import org.di.digital.service.CaseInterrogationReformulateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/interrogation")
@RequiredArgsConstructor
public class CaseInterrogationReformulateController {

    private final CaseInterrogationReformulateService caseInterrogationReformulateService;

    @PostMapping("/question/reformulate")
    public ResponseEntity<ReformulateQuestionResponse> reformulateQuestion(
            @RequestBody ReformulateQuestionRequest request) {
        return ResponseEntity.ok(caseInterrogationReformulateService.reformulateQuestion(request));
    }
}
