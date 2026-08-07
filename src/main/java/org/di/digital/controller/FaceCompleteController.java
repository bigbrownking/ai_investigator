package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.auth.JwtResponse;
import org.di.digital.service.face.FaceCompleteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/face-id")
@RequiredArgsConstructor
public class FaceCompleteController {

    private final FaceCompleteService faceCompleteService;

    @PostMapping("/face-complete")
    public ResponseEntity<JwtResponse> faceComplete(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        JwtResponse response = faceCompleteService.complete(authHeader, body.get("jobId"));
        return ResponseEntity.ok(response);
    }
}