package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.request.interrogation.MarkReformulatedRequest;
import org.di.digital.dto.request.interrogation.ReformulateQuestionRequest;
import org.di.digital.dto.response.interrogation.ReformulateQuestionResponse;
import org.di.digital.service.interrogation.CaseInterrogationReformulateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    @PatchMapping("/question/reformulate/accept")
    public ResponseEntity<Void> markAsReformulated(
            @RequestBody MarkReformulatedRequest request,
            Authentication authentication) {
        caseInterrogationReformulateService.markAsReformulated(request, authentication.getName());
        return ResponseEntity.ok().build();
    }
}
