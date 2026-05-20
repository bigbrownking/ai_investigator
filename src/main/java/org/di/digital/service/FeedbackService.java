package org.di.digital.service;

import org.di.digital.dto.request.ReviewRequest;
import org.di.digital.dto.request.SupportTicketRequest;
import org.di.digital.dto.response.ReviewDto;
import org.di.digital.dto.response.SupportTicketDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FeedbackService {
    SupportTicketDto createSupportTicket(SupportTicketRequest request, List<MultipartFile> photos, String email);
    ReviewDto createReview(ReviewRequest request, MultipartFile file, String email);
}
