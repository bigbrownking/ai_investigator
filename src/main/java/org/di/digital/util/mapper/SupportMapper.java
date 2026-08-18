package org.di.digital.util.mapper;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.response.*;
import org.di.digital.dto.response.admin.AppealDto;
import org.di.digital.dto.response.support.*;
import org.di.digital.model.Log;
import org.di.digital.model.enums.dictionary.ModuleType;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.support.Review;
import org.di.digital.model.support.ReviewItem;
import org.di.digital.model.support.SupportTicket;
import org.di.digital.model.user.Appeal;
import org.di.digital.model.user.User;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static org.di.digital.util.requests.UserUtil.getCurrentUser;

@Component
@RequiredArgsConstructor
public class SupportMapper {

    private final FileUrlResolver fileUrls;
    private final LocalizationHelper localizationHelper;

    public AppealDto toAppealDto(Appeal appeal) {
        User u = appeal.getUser();
        return AppealDto.builder()
                .id(appeal.getId())
                .userId(u.getId())
                .userName(u.getName())
                .userSurname(u.getSurname())
                .userFathername(u.getFathername())
                .userEmail(u.getEmail())
                .profession(u.getProfession() != null ? u.getProfession().getRuName() : null)
                .rank(u.getRank() != null ? u.getRank().getRuName() : null)
                .administration(u.getAdministration() != null ? u.getAdministration().getRuName() : null)
                .regionId(appeal.getRegion() != null ? appeal.getRegion().getId() : null)
                .regionName(localizationHelper.getLocalizedName(
                        appeal.getRegion(), getCurrentUser().getSettings().getLanguage()))
                .status(appeal.getStatus().getDescription())
                .createdAt(appeal.getCreatedAt())
                .reviewedAt(appeal.getReviewedAt())
                .build();
    }

    public LogDto toLogDto(Log log) {
        return LogDto.builder()
                .id(log.getId())
                .timestamp(log.getTimestamp())
                .level(log.getLevel().name())
                .action(log.getAction().getDescription())
                .description(log.getDescription())
                .caseNumber(log.getCaseNumber())
                .email(log.getEmail())
                .ipAddress(log.getIpAddress())
                .build();
    }

    public SupportTicketDto toSupportTicketDto(SupportTicket ticket) {
        List<SupportTicketPhotoDto> photos = ticket.getPhotos().stream()
                .map(p -> SupportTicketPhotoDto.builder()
                        .id(p.getId())
                        .originalFileName(p.getOriginalFileName())
                        .contentType(p.getContentType())
                        .previewUrl(fileUrls.preview(p.getFileUrl()))
                        .downloadUrl(fileUrls.download(p.getFileUrl(), p.getOriginalFileName()))
                        .build())
                .toList();

        User user = ticket.getUser();
        return SupportTicketDto.builder()
                .id(ticket.getId())
                .fio(user.getFio())
                .region(user.getRegion().getRuName())
                .profession(user.getProfession().getRuName())
                .message(ticket.getMessage())
                .phoneNumber(ticket.getPhoneNumber())
                .createdAt(ticket.getCreatedAt())
                .photos(photos)
                .build();
    }

    public ReviewDto toReviewDto(Review review) {
        User user = review.getUser();
        return ReviewDto.builder()
                .id(review.getId())
                .subject(review.getSubject())
                .createdAt(review.getCreatedAt())
                .fio(user != null ? user.getFio() : null)
                .region(user != null && user.getRegion() != null ? user.getRegion().getRuName() : null)
                .profession(user != null && user.getProfession() != null ? user.getProfession().getRuName() : null)
                .items(review.getItems() == null ? List.of()
                        : review.getItems().stream().map(this::toReviewItemDto).collect(Collectors.toList()))
                .build();
    }

    private ReviewItemDto toReviewItemDto(ReviewItem item) {
        ModuleType module = resolveModule(item.getModule());

        List<ReviewFileDto> files = item.getFiles() == null ? List.of()
                : item.getFiles().stream()
                .map(f -> ReviewFileDto.builder()
                        .originalFileName(f.getOriginalFileName())
                        .contentType(f.getContentType())
                        .previewUrl(fileUrls.preview(f.getFileUrl()))
                        .downloadUrl(fileUrls.download(f.getFileUrl(), f.getOriginalFileName()))
                        .build())
                .collect(Collectors.toList());

        return ReviewItemDto.builder()
                .moduleCode(item.getModule())
                .moduleName(module != null ? module.localized(UserSettingsLanguage.RU) : null)
                .message(item.getMessage())
                .files(files)
                .build();
    }

    private ModuleType resolveModule(String code) {
        if (code == null) return null;
        try {
            return ModuleType.from(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}