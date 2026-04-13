package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.LoginRequest;
import org.di.digital.dto.request.RefreshTokenRequest;
import org.di.digital.dto.request.SignUpRequest;
import org.di.digital.dto.response.JwtResponse;
import org.di.digital.model.*;
import org.di.digital.model.enums.*;
import org.di.digital.repository.*;
import org.di.digital.security.crypto.RsaDecryptor;
import org.di.digital.security.jwt.JwtTokenUtil;
import org.di.digital.service.AuthService;
import org.di.digital.service.LogService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,}$"
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RegionRepository regionRepository;
    private final AdministrationRepository administrationRepository;
    private final ProfessionRepository professionRepository;
    private final AppealRepository appealRepository;
    private final LogService logService;
    private final JwtTokenUtil jwtTokenUtil;
    private final PasswordEncoder passwordEncoder;
    private final RsaDecryptor rsaDecryptor;
    private final NotificationService notificationService;

    private String decryptAndValidatePassword(String encryptedPassword) {
        String raw = rsaDecryptor.decrypt(encryptedPassword);
        if (!PASSWORD_PATTERN.matcher(raw).matches()) {
            throw new IllegalStateException(
                    "Пароль не соответствует требованиям: минимум 8 символов, " +
                            "хотя бы одна заглавная буква, одна строчная, одна цифра и один спецсимвол"
            );
        }
        return raw;
    }

    @Override
    public String signupAlisher(SignUpRequest request) {
        log.info("Creating new user: {} {}", request.getName(), request.getSurname());

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Email already exists: {}", request.getEmail());
            return "Email is already registered";
        }

        String rawPassword = request.getPassword();

        Role userRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new RuntimeException("Default role not found"));

        User user = User.builder()
                .email(request.getEmail())
                .iin(request.getIin())
                .name(request.getName())
                .surname(request.getSurname())
                .fathername(request.getFathername())
                .profession(null)
                .administration(null)
                .region(null)
                .password(passwordEncoder.encode(rawPassword))
                .roles(new HashSet<>() {{
                    add(userRole);
                }})
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
    public String signup(SignUpRequest request) {
        log.info("Creating new user: {} {}", request.getName(), request.getSurname());

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Email already exists: {}", request.getEmail());
            return "Email is already registered";
        }

        String rawPassword = decryptAndValidatePassword(request.getPassword());

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("Default role not found"));

        Region region = null;
        if (request.getRegionId() != null) {
            region = regionRepository.findById(request.getRegionId())
                    .orElseThrow(() -> new RuntimeException("Region not found"));
        }

        Profession profession = null;
        if (request.getProfessionId() != null) {
            profession = professionRepository.findById(request.getProfessionId())
                    .orElseThrow(() -> new RuntimeException("Profession not found"));
        }

        Administration administration = null;
        if (request.getAdministrationId() != null) {
            administration = administrationRepository.findById(request.getAdministrationId())
                    .orElseThrow(() -> new RuntimeException("Administration not found"));
        }

        User user = User.builder()
                .email(request.getEmail())
                .iin(request.getIin())
                .name(request.getName())
                .surname(request.getSurname())
                .fathername(request.getFathername())
                .region(region)
                .profession(profession)
                .administration(administration)
                .password(passwordEncoder.encode(rawPassword))
                .roles(new HashSet<>() {{
                    add(userRole);
                }})
                .active(false)
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

        Appeal appeal = Appeal.builder()
                .user(savedUser)
                .region(region)
                .status(AppealStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        appealRepository.save(appeal);

        if (region != null && region.getAdmin() != null) {
            notificationService.sendNotificationToUser(
                    region.getAdmin().getEmail(),
                    "Новый пользователь хочет зарегистрироваться в вашем регионе: "
                            + request.getName() + " " + request.getSurname()
            );
        }
        return "User registered successfully";
    }


    @Override
    public String signupRegAdmin(SignUpRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Email already exists: {}", request.getEmail());
            return "Email is already registered";
        }

        String rawPassword = decryptAndValidatePassword(request.getPassword());

        Role userRole = roleRepository.findByName("REG_ADMIN")
                .orElseThrow(() -> new RuntimeException("Default role not found"));

        Region region = null;
        if (request.getRegionId() != null) {
            region = regionRepository.findById(request.getRegionId())
                    .orElseThrow(() -> new RuntimeException("Region not found"));
        }

        Profession profession = null;
        if (request.getProfessionId() != null) {
            profession = professionRepository.findById(request.getProfessionId())
                    .orElseThrow(() -> new RuntimeException("Profession not found"));
        }

        Administration administration = null;
        if (request.getAdministrationId() != null) {
            administration = administrationRepository.findById(request.getAdministrationId())
                    .orElseThrow(() -> new RuntimeException("Administration not found"));
        }

        User user = User.builder()
                .email(request.getEmail())
                .iin(request.getIin())
                .name(request.getName())
                .surname(request.getSurname())
                .fathername(request.getFathername())
                .region(region)
                .profession(profession)
                .administration(administration)
                .password(passwordEncoder.encode(rawPassword))
                .roles(new HashSet<>() {{
                    add(userRole);
                }})
                .active(false)
                .build();

        if (region != null) {
            region.setAdmin(user);
        }

        UserSettings userSettings = UserSettings.builder()
                .level(UserSettingsDetalizationLevel.HIGH)
                .theme(UserSettingsTheme.LIGHT)
                .language(UserSettingsLanguage.RU)
                .user(user)
                .build();

        user.setSettings(userSettings);
        User savedUser = userRepository.save(user);
        log.info("User created successfully with ID: {}", savedUser.getId());
        return "Reg admin registered successfully";
    }

    @Override
    public JwtResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if(!user.isActive()){
            throw new IllegalStateException("Пользователь еще не был подтвержден админом");
        }
        String rawPassword = rsaDecryptor.decrypt(request.getPassword());

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtTokenUtil.generateTokenFromUsername(user.getEmail());
        String refreshToken = jwtTokenUtil.generateRefreshToken(user.getEmail());

        logService.log(
                String.format("User logged in %s user", request.getEmail()),
                LogLevel.INFO,
                LogAction.LOGIN,
                null,
                user.getEmail()
        );

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
}