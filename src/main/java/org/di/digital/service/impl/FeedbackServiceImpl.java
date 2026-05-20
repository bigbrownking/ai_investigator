package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.ReviewRequest;
import org.di.digital.dto.request.SupportTicketRequest;
import org.di.digital.dto.response.ReviewDto;
import org.di.digital.dto.response.SupportTicketDto;
import org.di.digital.dto.response.SupportTicketPhotoDto;
import org.di.digital.model.*;
import org.di.digital.model.support.Review;
import org.di.digital.model.support.SupportTicket;
import org.di.digital.model.support.SupportTicketPhoto;
import org.di.digital.repository.UserRepository;
import org.di.digital.repository.support.ReviewRepository;
import org.di.digital.repository.support.SupportTicketRepository;
import org.di.digital.service.FeedbackService;
import org.di.digital.service.MinioService;
import org.di.digital.util.Mapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final SupportTicketRepository supportTicketRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final MinioService minioService;
    private final Mapper mapper;

    @Override
    @Transactional
    public SupportTicketDto createSupportTicket(SupportTicketRequest request, List<MultipartFile> photos, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        SupportTicket ticket = SupportTicket.builder()
                .user(user)
                .message(request.getMessage())
                .phoneNumber(request.getPhoneNumber())
                .photos(new ArrayList<>())
                .build();

        if (photos != null && !photos.isEmpty()) {
            for (MultipartFile photo : photos) {
                if (photo == null || photo.isEmpty()) continue;

                CaseFile uploaded = minioService.uploadFile(photo, "support/" + email);

                SupportTicketPhoto ticketPhoto = SupportTicketPhoto.builder()
                        .ticket(ticket)
                        .fileUrl(uploaded.getFileUrl())
                        .originalFileName(uploaded.getOriginalFileName())
                        .contentType(uploaded.getContentType())
                        .build();

                ticket.getPhotos().add(ticketPhoto);
            }
        }

        SupportTicket saved = supportTicketRepository.save(ticket);
        log.info("Support ticket created: id={}, userEmail={}", saved.getId(), email);

        return mapper.mapToSupportTicketDto(saved);
    }

    @Override
    @Transactional
    public ReviewDto createReview(ReviewRequest request, MultipartFile file, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        String fileUrl = null;
        String originalFileName = null;
        String contentType = null;

        if (file != null && !file.isEmpty()) {
            CaseFile uploaded = minioService.uploadFile(file, "reviews/" + email);
            fileUrl = uploaded.getFileUrl();
            originalFileName = uploaded.getOriginalFileName();
            contentType = uploaded.getContentType();
        }

        Review review = Review.builder()
                .user(user)
                .subject(request.getSubject())
                .message(request.getMessage())
                .fileUrl(fileUrl)
                .originalFileName(originalFileName)
                .contentType(contentType)
                .build();

        Review saved = reviewRepository.save(review);
        log.info("Review created: id={}, userEmail={}", saved.getId(), email);

        return mapper.mapToReviewDto(saved);
    }


}