package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import org.di.digital.model.appeal.AppealNotification;
import org.di.digital.service.appeal.AppealService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/appeals")
@RequiredArgsConstructor
public class AppealController {

    private final AppealService appealService;

    @GetMapping("/notifications")
    public ResponseEntity<List<AppealNotification>> getMyNotifications(Authentication authentication) {
        return ResponseEntity.ok(appealService.getMyNotifications(authentication.getName()));
    }

    @PatchMapping("/notifications/read")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        appealService.markAllRead(authentication.getName());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markOneRead(@PathVariable Long id, Authentication authentication) {
        appealService.markOneRead(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(Map.of("count", appealService.getUnreadCount(authentication.getName())));
    }
}