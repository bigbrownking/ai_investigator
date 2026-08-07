package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.face.FaceResetResponse;
import org.di.digital.service.face.FaceManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/face-id")
@RequiredArgsConstructor
public class FaceManagementController {

    private final FaceManagementService faceManagementService;

    @DeleteMapping("/me")
    public ResponseEntity<FaceResetResponse> deleteMyFace(Authentication authentication) {
        return ResponseEntity.ok(faceManagementService.resetOwnFace(authentication.getName()));
    }

    @DeleteMapping("/user/{userId}")
    public ResponseEntity<FaceResetResponse> deleteUserFace(
            Authentication authentication,
            @PathVariable Long userId) {
        return ResponseEntity.ok(faceManagementService.resetUserFace(authentication.getName(), userId));
    }
}