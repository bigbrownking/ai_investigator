package org.di.digital.service.impl.auth;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.client.FaceAuthClient;
import org.di.digital.dto.request.auth.*;
import org.di.digital.dto.response.auth.JwtResponse;
import org.di.digital.exception.NotFoundException;
import org.di.digital.model.enums.appeal.AppealStatus;
import org.di.digital.model.enums.log.LogAction;
import org.di.digital.model.enums.log.LogLevel;
import org.di.digital.model.enums.settings.UserSettingsDetalizationLevel;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.enums.settings.UserSettingsTheme;
import org.di.digital.model.user.*;
import org.di.digital.repository.user.*;
import org.di.digital.security.crypto.RsaDecryptor;
import org.di.digital.security.jwt.JwtTokenUtil;
import org.di.digital.security.jwt.OneTimeTokenService;
import org.di.digital.security.jwt.PreAuthTokenUtil;
import org.di.digital.service.LogService;
import org.di.digital.service.auth.AuthService;
import org.di.digital.service.impl.core.EmailService;
import org.di.digital.service.impl.core.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.password.expiry-days}")
    private int passwordExpiryDays;

    @Value("${app.password.history-size}")
    private int passwordHistorySize;

    @Value("${app.login.max-attempts}")
    private int maxLoginAttempts;

    @Value("${app.login.lock-duration-minutes}")
    private int lockDurationMinutes;

    private final PasswordHistoryRepository passwordHistoryRepository;

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,}$"
    );
    @Value("${app.whitelist-iins:}")
    private Set<String> whitelistIins;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RegionRepository regionRepository;
    private final AdministrationRepository administrationRepository;
    private final RankRepository rankRepository;
    private final ProfessionRepository professionRepository;
    private final AppealRepository appealRepository;
    private final LogService logService;
    private final JwtTokenUtil jwtTokenUtil;
    private final PasswordEncoder passwordEncoder;
    private final RsaDecryptor rsaDecryptor;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final FaceAuthClient faceAuthClient;
    private final PreAuthTokenUtil preAuthTokenUtil;
    private final OneTimeTokenService oneTimeTokenService;

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
                .orElseThrow(() -> new NotFoundException("Роль не найдена"));

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
                .orElseThrow(() -> new NotFoundException("Роль не найдена"));

        boolean isZamDep = false;
        Profession profession = null;
        if (request.getProfessionId() != null) {
            profession = professionRepository.findById(request.getProfessionId())
                    .orElseThrow(() -> new NotFoundException("Профессия не найдена"));
            if (profession.getId() == 7) {
                isZamDep = true;
            }
        }

        Region region = null;
        if (request.getRegionId() != null) {
            region = regionRepository.findById(request.getRegionId())
                    .orElseThrow(() -> new NotFoundException("Регион не найден"));
        }

        Rank rank = null;
        if (request.getRankId() != null) {
            rank = rankRepository.findById(request.getRankId())
                    .orElseThrow(() -> new NotFoundException("Звание не найдено"));
        }

        Administration administration = null;
        if (request.getAdministrationId() != null && !isZamDep) {
            administration = administrationRepository.findById(request.getAdministrationId())
                    .orElseThrow(() -> new NotFoundException("Управление не найдено"));
        }

        User user = User.builder()
                .email(request.getEmail())
                .iin(request.getIin())
                .name(request.getName())
                .surname(request.getSurname())
                .fathername(request.getFathername())
                .region(region)
                .profession(profession)
                .rank(rank)
                .administration(administration)
                .password(passwordEncoder.encode(rawPassword))
                .passwordChangedAt(LocalDateTime.now())
                .roles(new HashSet<>() {{
                    add(userRole);
                }})
                .active(false)
                .deleted(false)
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
        if (request.getFaceReferenceJobId() != null) {
            Map<String, Object> res = faceAuthClient.adopt(
                    request.getFaceReferenceJobId(), request.getJobToken(), savedUser.getId());
            if (!Boolean.TRUE.equals(res.get("adopted"))) {
                throw new IllegalStateException("Не удалось привязать Face ID к регистрации");
            }
            savedUser.setFaceEnabled(true);
            userRepository.save(savedUser);
        }
        Appeal appeal = Appeal.builder()
                .user(savedUser)
                .region(region)
                .status(AppealStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        appealRepository.save(appeal);

        if (region != null && !region.getAdmins().isEmpty()) {
            region.getAdmins().forEach(admin ->
                    notificationService.sendNotificationToUser(
                            admin.getEmail(),
                            "Новый пользователь хочет зарегистрироваться в вашем регионе: "
                                    + request.getName() + " " + request.getSurname()
                    )
            );
        }
        logService.log(
                String.format("User registered as %s user", request.getIin()),
                LogLevel.INFO,
                LogAction.SIGNUP,
                null,
                user.getEmail()
        );
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
                .orElseThrow(() -> new NotFoundException("Роль не найдена"));

        Region region = null;
        if (request.getRegionId() != null) {
            region = regionRepository.findById(request.getRegionId())
                    .orElseThrow(() -> new NotFoundException("Регион не найден"));
        }

        Profession profession = null;
        if (request.getProfessionId() != null) {
            profession = professionRepository.findById(request.getProfessionId())
                    .orElseThrow(() -> new NotFoundException("Профессия не найдена"));
        }

        Administration administration = null;
        if (request.getAdministrationId() != null) {
            administration = administrationRepository.findById(request.getAdministrationId())
                    .orElseThrow(() -> new NotFoundException("Управление не найдено"));
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
            region.getAdmins().add(user);
            regionRepository.save(region);
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
        User user = userRepository.findByIin(request.getIin())
                .orElseThrow(() -> new NotFoundException("Пользователь с таким ИИН не найден: " + request.getIin()));

        if (user.isDeleted()) throw new IllegalStateException("Данный аккаунт удален");
        if (!user.isActive()) throw new IllegalStateException("Пользователь еще не был подтвержден админом");

        if (isLocked(user)) {
            long minutesLeft = Duration.between(LocalDateTime.now(), user.getLockTime()).toMinutes() + 1;
            throw new IllegalStateException("Аккаунт заблокирован из-за превышения числа попыток входа. Повторите через " + minutesLeft + " мин.");
        }

        String rawPassword = rsaDecryptor.decrypt(request.getPassword());
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            registerFailedAttempt(user);
            throw new IllegalStateException("Вы ввели неправильный пароль");
        }

        resetFailedAttempts(user);

        logService.log(String.format("User logged in %s user", request.getIin()),
                LogLevel.INFO, LogAction.LOGIN, null, user.getEmail());

        // Сценарий 1: пароль истёк -> pre-auth PASSWORD_RESET.
        if (isPasswordExpired(user)) {
            String preAuth = preAuthTokenUtil.generatePasswordReset(user.getId(), user.getEmail());
            return JwtResponse.builder()
                    .username(user.getEmail())
                    .passwordExpired(true)
                    .preAuthToken(preAuth)
                    .build();
        }

        // Whitelist
        if (user.getIin() != null && whitelistIins.contains(user.getIin())) {
            String access = jwtTokenUtil.generateTokenFromUsername(user.getEmail());
            String refresh = jwtTokenUtil.generateRefreshToken(user.getEmail());
            return JwtResponse.builder()
                    .token(access).refreshToken(refresh)
                    .type("Bearer").username(user.getEmail())
                    .faceEnabled(user.isFaceEnabled())
                    .requiresFaceId(false)
                    .faceEnrollmentRequired(false)
                    .build();
        }

        // Сценарий 2: лицо ещё НЕ поставлено -> pre-auth ENROLLMENT.
        if (!user.isFaceEnabled()) {
            String preAuth = preAuthTokenUtil.generateFace(user.getId(), user.getEmail(), true);
            return JwtResponse.builder()
                    .type("Bearer").username(user.getEmail())
                    .faceEnabled(false)
                    .requiresFaceId(true)
                    .faceEnrollmentRequired(true)
                    .preAuthToken(preAuth)
                    .build();
        }

        // Сценарий 3: лицо есть -> pre-auth AUTH, фронт проводит verify.
        String preAuth = preAuthTokenUtil.generateFace(user.getId(), user.getEmail(), false);
        return JwtResponse.builder()
                .type("Bearer").username(user.getEmail())
                .faceEnabled(true)
                .requiresFaceId(true)
                .faceEnrollmentRequired(false)
                .preAuthToken(preAuth)
                .build();
    }

    private boolean isPasswordExpired(User user) {
        if (user.getIin() != null && whitelistIins.contains(user.getIin())) {
            return false;
        }
        LocalDateTime changedAt = user.getPasswordChangedAt() != null
                ? user.getPasswordChangedAt()
                : user.getCreatedDate();

        if (changedAt == null) {
            return false;
        }

        return changedAt.isBefore(LocalDateTime.now().minusDays(passwordExpiryDays));
    }
    private boolean isLocked(User user) {
        if (user.getLockTime() == null) return false;

        if (user.getLockTime().isAfter(LocalDateTime.now())) {
            return true;
        }
        user.setLockTime(null);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);
        return false;
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= maxLoginAttempts) {
            user.setLockTime(LocalDateTime.now().plusMinutes(lockDurationMinutes));
            log.info("User {} locked for {} min after {} failed attempts",
                    user.getIin(), lockDurationMinutes, attempts);
        }
        userRepository.save(user);
    }

    private void resetFailedAttempts(User user) {
        if (user.getFailedLoginAttempts() != 0 || user.getLockTime() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockTime(null);
            userRepository.save(user);
        }
    }

    @Override
    public JwtResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        log.info("Attempting to refresh token");

        if (!jwtTokenUtil.validateRefreshToken(refreshToken)) {
            log.error("Invalid refresh token");
            throw new IllegalStateException("Invalid refresh token");
        }

        String username = jwtTokenUtil.getUsernameFromJwtToken(refreshToken);

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден: " + username));

        String newAccessToken = jwtTokenUtil.generateTokenFromUsername(user.getEmail());
        String newRefreshToken = jwtTokenUtil.generateRefreshToken(user.getEmail());

        log.info("Token refreshed successfully for user: {}", username);

        return JwtResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshToken)
                .type("Bearer")
                .faceEnabled(user.isFaceEnabled())
                .username(user.getEmail())
                .build();
    }

    @Override
    @Transactional
    public String forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        String origin = request.getOrigin() != null ? request.getOrigin() : frontendUrl;
        String resetLink = origin + "/reset-password?token=" + token;
        emailService.sendResetPasswordEmail(request.getEmail(), resetLink);

        return "Письмо отправлено на " + request.getEmail();
    }

    @Override
    @Transactional
    public String resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByResetToken(request.getToken())
                .orElseThrow(() -> new IllegalStateException("Неверный токен"));

        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Срок действия ссылки истёк");
        }

        String rawPassword = decryptAndValidatePassword(request.getNewPassword());

        if (passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new IllegalStateException("Новый пароль не должен совпадать с текущим");
        }

        List<PasswordHistory> history = passwordHistoryRepository.findByUserOrderByChangedAtDesc(user);

        for (PasswordHistory h : history) {
            if (passwordEncoder.matches(rawPassword, h.getPasswordHash())) {
                throw new IllegalStateException("Этот пароль уже использовался ранее");
            }
        }

        passwordHistoryRepository.save(PasswordHistory.builder()
                .user(user)
                .passwordHash(user.getPassword())
                .changedAt(LocalDateTime.now())
                .build());


        log.info("Saving new password for user: {}", user.getEmail());
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Password saved successfully for user: {}", user.getEmail());

        List<PasswordHistory> all = passwordHistoryRepository.findByUserOrderByChangedAtDesc(user);

        if (all.size() > passwordHistorySize) {
            passwordHistoryRepository.deleteAll(all.subList(passwordHistorySize, all.size()));
        }

        return "Пароль успешно изменён";
    }

    private String applyNewPassword(User user, String encryptedNewPassword) {
        String rawNew = decryptAndValidatePassword(encryptedNewPassword);

        if (passwordEncoder.matches(rawNew, user.getPassword())) {
            throw new IllegalStateException("Новый пароль не должен совпадать с текущим");
        }

        List<PasswordHistory> history = passwordHistoryRepository.findByUserOrderByChangedAtDesc(user);
        for (PasswordHistory h : history) {
            if (passwordEncoder.matches(rawNew, h.getPasswordHash())) {
                throw new IllegalStateException("Этот пароль уже использовался ранее");
            }
        }

        passwordHistoryRepository.save(PasswordHistory.builder()
                .user(user)
                .passwordHash(user.getPassword())
                .changedAt(LocalDateTime.now())
                .build());

        user.setPassword(passwordEncoder.encode(rawNew));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        List<PasswordHistory> all = passwordHistoryRepository.findByUserOrderByChangedAtDesc(user);
        if (all.size() > passwordHistorySize) {
            passwordHistoryRepository.deleteAll(all.subList(passwordHistorySize, all.size()));
        }

        return "Пароль успешно изменён";
    }

    @Override
    @Transactional
    public String changeExpiredPassword(String preAuthToken, String encryptedNewPassword) {
        Claims claims = preAuthTokenUtil.validatePasswordReset(preAuthToken);

        String jti = preAuthTokenUtil.jti(claims);
        if (jti == null) {
            throw new IllegalStateException("Недействительный токен смены пароля");
        }

        boolean firstUse = oneTimeTokenService.markUsed(jti, preAuthTokenUtil.expiresAt(claims));
        if (!firstUse) {
            throw new IllegalStateException("Токен смены пароля уже был использован");
        }

        User user = userRepository.findById(preAuthTokenUtil.userId(claims))
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        return applyNewPassword(user, encryptedNewPassword);
    }
}