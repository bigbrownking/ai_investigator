package org.di.digital.service;

import org.di.digital.dto.request.support.ReviewRequest;
import org.di.digital.dto.request.support.SupportTicketRequest;
import org.di.digital.dto.response.support.ReviewDto;
import org.di.digital.dto.response.support.SupportTicketDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FeedbackService {
    SupportTicketDto createSupportTicket(SupportTicketRequest request, List<MultipartFile> photos, String email);
    ReviewDto createReview(ReviewRequest request, MultipartFile file, String email);
}
