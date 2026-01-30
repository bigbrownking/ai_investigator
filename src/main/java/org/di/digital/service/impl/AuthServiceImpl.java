package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.LoginRequest;
import org.di.digital.dto.request.RefreshTokenRequest;
import org.di.digital.dto.request.SignUpRequest;
import org.di.digital.dto.response.JwtResponse;
import org.di.digital.model.Role;
import org.di.digital.model.User;
import org.di.digital.model.UserSettings;
import org.di.digital.model.enums.*;
import org.di.digital.repository.RoleRepository;
import org.di.digital.repository.UserRepository;
import org.di.digital.security.crypto.RsaDecryptor;
import org.di.digital.security.jwt.JwtTokenUtil;
import org.di.digital.service.AuthService;
import org.di.digital.service.LogService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final LogService logService;
    private final JwtTokenUtil jwtTokenUtil;
    private final PasswordEncoder passwordEncoder;
    private final RsaDecryptor rsaDecryptor;


    @Override
    public String signup(SignUpRequest request) {
        log.info("Creating new user: {} {}", request.getName(), request.getSurname());

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Email already exists: {}", request.getEmail());
            return "Email is already registered";
        }

        Role userRole = roleRepository.findByName("LEVEL_3_USER")
                .orElseThrow(() -> new RuntimeException("Default role not found"));

        User user = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .name(request.getName())
                .surname(request.getSurname())
                .fathername(request.getFathername())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(new HashSet<>(){{add(userRole);}})
                .active(true)
                .build();

        UserSettings userSettings = UserSettings.builder()
                .level(UserSettingsDetalizationLevel.HIGH)
                .theme(UserSettingsTheme.LIGHT)
                .language(UserSettingsLanguage.RU)
                .user(user)
                .build();

        user.setSettings(userSettings);
        User savedUser = userRepository.save(user);
        log.info("User created successfully with ID: {}", savedUser.getId());
        return "User registered successfully";
    }

    @Override
    public JwtResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));


       // String rawPassword = rsaDecryptor.decrypt(request.getPassword());

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtTokenUtil.generateTokenFromUsername(user.getEmail());
        String refreshToken = jwtTokenUtil.generateRefreshToken(user.getEmail());

        return JwtResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .type("Bearer")
                .username(user.getEmail())
                .build();
    }


    @Override
    public JwtResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        log.info("Attempting to refresh token");

        if (!jwtTokenUtil.validateRefreshToken(refreshToken)) {
            log.error("Invalid refresh token");
            throw new RuntimeException("Invalid refresh token");
        }

        String username = jwtTokenUtil.getUsernameFromJwtToken(refreshToken);

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));


        String newAccessToken = jwtTokenUtil.generateTokenFromUsername(user.getEmail());
        String newRefreshToken = jwtTokenUtil.generateRefreshToken(user.getEmail());

        log.info("Token refreshed successfully for user: {}", username);

        return JwtResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshToken)
                .type("Bearer")
                .username(user.getEmail())
                .build();
    }

    @Override
    public void logout(String refreshToken) {

    }
}
