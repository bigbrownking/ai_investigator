package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.ReviewRequest;
import org.di.digital.dto.request.SupportTicketRequest;
import org.di.digital.dto.response.ReviewDto;
import org.di.digital.dto.response.SupportTicketDto;
import org.di.digital.service.FeedbackService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping(value = "/support", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SupportTicketDto> createSupportTicket(
            @RequestPart("request") SupportTicketRequest request,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos,
            Authentication authentication) {
        return ResponseEntity.ok(feedbackService.createSupportTicket(request, photos, authentication.getName()));
    }

    @PostMapping(value = "/review", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReviewDto> createReview(
            @RequestPart("request") ReviewRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file,
            Authentication authentication) {
        return ResponseEntity.ok(feedbackService.createReview(request, file, authentication.getName()));
    }
}