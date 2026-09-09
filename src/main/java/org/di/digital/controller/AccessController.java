package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.cases.access.GrantAccessRequest;
import org.di.digital.dto.request.cases.access.UpdateFileAccessRequest;
import org.di.digital.dto.response.access.MyAccessDto;
import org.di.digital.service.UserService;
import org.di.digital.service.cases.CaseAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/cases/{caseId}/access/{userId}")
public class AccessController {
    private final UserService userService;
    private final CaseAccessService caseAccessService;

    @GetMapping
    public ResponseEntity<MyAccessDto> getPermissions(
            @PathVariable Long caseId,
            @PathVariable Long userId) {
        log.info("Fetching permissions for {}", userId);
        return ResponseEntity.ok(userService.getPermissions(userId, caseId));
    }

    @PostMapping("/grant")
    public ResponseEntity<Void> grantAccess(
            @PathVariable Long caseId,
            @PathVariable Long userId,
            @RequestBody GrantAccessRequest request,
            Authentication authentication) {
        caseAccessService.grantAccess(caseId, userId, request, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/revoke")
    public ResponseEntity<Void> revokeAccess(
            @PathVariable Long caseId,
            @PathVariable Long userId,
            Authentication authentication) {
        caseAccessService.revokeAccess(caseId, userId, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/files")
    public ResponseEntity<Void> updateFileAccess(
            @PathVariable Long caseId,
            @PathVariable Long userId,
            @RequestBody UpdateFileAccessRequest request,
            Authentication authentication) {
        caseAccessService.updateFileAccess(caseId, userId, request, authentication.getName());
        return ResponseEntity.ok().build();
    }
}
