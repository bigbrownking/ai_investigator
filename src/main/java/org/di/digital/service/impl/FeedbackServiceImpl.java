package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.support.ReviewItemRequest;
import org.di.digital.dto.request.support.ReviewRequest;
import org.di.digital.dto.request.support.SupportTicketRequest;
import org.di.digital.dto.response.support.ReviewDto;
import org.di.digital.dto.response.support.SupportTicketDto;
import org.di.digital.exception.NotFoundException;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.enums.log.LogAction;
import org.di.digital.model.enums.log.LogLevel;
import org.di.digital.model.enums.dictionary.ModuleType;
import org.di.digital.model.support.*;
import org.di.digital.model.user.User;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.repository.support.ReviewRepository;
import org.di.digital.repository.support.SupportTicketRepository;
import org.di.digital.service.FeedbackService;
import org.di.digital.service.LogService;
import org.di.digital.service.core.MinioService;
import org.di.digital.util.Mapper;
import org.di.digital.util.PageCounter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final SupportTicketRepository supportTicketRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final MinioService minioService;
    private final LogService logService;
    private final Mapper mapper;
    private final PageCounter pageCounter;

    @Value("${files.max-pages-per-file}")
    private int maxPagesPerFile;

    @Value("${files.max-files-per-module}")
    private int maxFilesPerModule;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");



    @Override
    @Transactional
    public SupportTicketDto createSupportTicket(SupportTicketRequest request, List<MultipartFile> photos, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден: " + email));

        SupportTicket ticket = SupportTicket.builder()
                .user(user)
                .message(request.getMessage())
                .phoneNumber(request.getPhoneNumber())
                .photos(new ArrayList<>())
                .build();

        if (photos != null && !photos.isEmpty()) {
            String folder = "support/" + user.getFio() + "/" + LocalDate.now().format(DATE_FMT);

            for (MultipartFile photo : photos) {
                if (photo == null || photo.isEmpty()) continue;

                CaseFile uploaded = minioService.uploadFile(photo, folder, false);
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

        logService.log(
                String.format("Support ticket created: id=%d by user %s", saved.getId(), email),
                LogLevel.INFO,
                LogAction.SUPPORT_TICKET_CREATE,
                null,
                email
        );
        return mapper.mapToSupportTicketDto(saved);
    }

    @Override
    @Transactional
    public ReviewDto createReview(ReviewRequest request, MultipartHttpServletRequest multipart, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден: " + email));

        Review review = Review.builder()
                .user(user)
                .subject(request.getSubject())
                .items(new ArrayList<>())
                .build();

        List<ReviewItemRequest> items = request.getItems() != null ? request.getItems() : List.of();

        Set<String> seenModules = new HashSet<>();
        String date = LocalDate.now().format(DATE_FMT);

        List<String> uploadedUrls = new ArrayList<>();

        try {
            for (ReviewItemRequest itemReq : items) {
                ModuleType module = ModuleType.from(itemReq.getModule());

                if (module != null && !seenModules.add(module.name())) {
                    throw new IllegalArgumentException("Модуль указан дважды: " + module.name());
                }

                boolean hasMessage = itemReq.getMessage() != null && !itemReq.getMessage().isBlank();

                List<MultipartFile> rawFiles = module != null
                        ? multipart.getFiles("file_" + module.name())
                        : List.of();

                List<MultipartFile> files = rawFiles.stream()
                        .filter(f -> f != null && !f.isEmpty())
                        .toList();
                boolean hasFile = !files.isEmpty();

                if (!hasMessage && !hasFile) continue;

                if (files.size() > maxFilesPerModule) {
                    throw new IllegalArgumentException(String.format(
                            "Модуль \"%s\": превышен лимит файлов (%d из %d максимум).",
                            module != null ? module.name() : "?", files.size(), maxFilesPerModule));
                }

                ReviewItem item = ReviewItem.builder()
                        .module(module != null ? module.name() : null)
                        .message(itemReq.getMessage())
                        .files(new ArrayList<>())
                        .build();

                if (module != null && hasFile) {
                    String folder = "reviews/" + user.getFio() + "/" + date + "/" + module.name();

                    for (MultipartFile file : files) {
                        Integer pages = null;
                        try {
                            pages = pageCounter.countPages(file.getBytes(), file.getContentType());
                        } catch (Exception e) {
                            log.warn("Could not count pages for {}: {}", file.getOriginalFilename(), e.getMessage());
                        }

                        if (pages != null && pages > maxPagesPerFile) {
                            throw new IllegalArgumentException(String.format(
                                    "Файл \"%s\" содержит %d страниц. Максимум — %d страниц на файл.",
                                    file.getOriginalFilename(), pages, maxPagesPerFile));
                        }

                        CaseFile uploaded = minioService.uploadFile(file, folder, false);                        uploadedUrls.add(uploaded.getFileUrl());

                        ReviewItemFile itemFile = ReviewItemFile.builder()
                                .fileUrl(uploaded.getFileUrl())
                                .originalFileName(uploaded.getOriginalFileName())
                                .contentType(uploaded.getContentType())
                                .build();

                         item.addFile(itemFile);
                    }
                }

                review.addItem(item);
            }

            if (review.getItems().isEmpty()) {
                throw new IllegalArgumentException("Рецензия пустая: не заполнен ни один модуль");
            }

        } catch (RuntimeException e) {
            for (String url : uploadedUrls) {
                try {
                    minioService.deleteFile(url);
                } catch (Exception ignored) {}
            }
            throw e;
        }

        Review saved = reviewRepository.save(review);
        log.info("Review created: id={}, items={}, userEmail={}", saved.getId(), saved.getItems().size(), email);

        logService.log(
                String.format("Review created: id=%d, modules=%d by user %s",
                        saved.getId(), saved.getItems().size(), email),
                LogLevel.INFO,
                LogAction.REVIEW_CREATE,
                null,
                email
        );
        return mapper.mapToReviewDto(saved);
    }
}