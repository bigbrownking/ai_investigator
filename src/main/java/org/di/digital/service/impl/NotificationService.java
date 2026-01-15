package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.notification.FileProcessingNotification;
import org.di.digital.model.CaseFile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final SimpMessagingTemplate messagingTemplate;

    public void sendNotification(String userEmail, String caseNumber, CaseFile caseFile, String text, String errorMessage) {
        FileProcessingNotification notification = new FileProcessingNotification(
                caseFile.getId(),
                caseNumber,
                caseFile.getOriginalFileName(),
                caseFile.getStatus(),
                text,
                errorMessage,
                LocalDateTime.now()
        );

        messagingTemplate.convertAndSendToUser(
                userEmail,
                "/case/" + caseNumber + "/file/" + caseFile.getId(),
                notification
        );

        log.info("WebSocket notification sent for user{}, file {} in case {}",
                userEmail, caseFile.getId(), caseNumber);
    }
}
