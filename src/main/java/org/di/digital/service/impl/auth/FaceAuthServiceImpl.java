package org.di.digital.service.impl.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.auth.FaceLoginRequest;
import org.di.digital.dto.request.auth.LivenessVerifyRequest;
import org.di.digital.dto.response.auth.JwtResponse;
import org.di.digital.dto.response.auth.ChallengeResponse;
import org.di.digital.dto.response.auth.FaceStatusResponse;
import org.di.digital.dto.response.auth.LivenessChallengeResponse;
import org.di.digital.dto.response.auth.LivenessVerifyResponse;
import org.di.digital.model.enums.LivenessStep;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.model.user.User;
import org.di.digital.model.user.UserFaceTemplate;
import org.di.digital.repository.user.UserFaceTemplateRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.security.jwt.JwtTokenUtil;
import org.di.digital.service.auth.FaceAuthService;
import org.di.digital.service.LogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceAuthServiceImpl implements FaceAuthService {

    @Value("${face.auth.threshold}")
    private double threshold;

    @Value("${face.auth.max-templates}")
    private int maxTemplates;

    @Value("${face.auth.descriptor-length}")
    private int descriptorLength;

    @Value("${face.auth.liveness-ttl-seconds}")
    private int livenessTtlSeconds;

    @Value("${face.auth.liveness-token-ttl-minutes}")
    private int livenessTokenTtlMinutes;

    @Value("${face.auth.challenge-ttl-millis}")
    private int challengeTtlMillis;

    private static final List<LivenessStep> ALL_STEPS = List.of(LivenessStep.values());

    private final UserFaceTemplateRepository faceTemplateRepository;
    private final UserRepository userRepository;
    private final JwtTokenUtil jwtTokenUtil;
    private final LogService logService;

    private final Map<String, String> challengeStore = new ConcurrentHashMap<>();
    private final Map<String, LivenessSession> livenessStore = new ConcurrentHashMap<>();
    private final Map<String, LivenessTokenData> livenessTokenStore = new ConcurrentHashMap<>();

    @Data
    @AllArgsConstructor
    private static class LivenessSession {
        private String iin;
        private List<LivenessStep> steps;
        private LocalDateTime expiresAt;
        private boolean used;
    }

    @Data
    @AllArgsConstructor
    private static class LivenessTokenData {
        private String iin;
        private LocalDateTime expiresAt;
    }

    @Override
    public ChallengeResponse generateChallenge(String iin) {
        userRepository.findByIin(iin)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

        String challengeId = UUID.randomUUID().toString();
        challengeStore.put(challengeId, iin);

        new Thread(() -> {
            try { Thread.sleep(challengeTtlMillis); } catch (InterruptedException ignored) {}
            challengeStore.remove(challengeId);
        }).start();

        return ChallengeResponse.builder()
                .challengeId(challengeId)
                .build();
    }

    @Override
    public LivenessChallengeResponse generateLivenessChallenge(String iin) {
        userRepository.findByIin(iin)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

        List<LivenessStep> steps = new java.util.ArrayList<>(ALL_STEPS);
        java.util.Collections.shuffle(steps);

        String livenessId = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(livenessTtlSeconds);

        livenessStore.put(livenessId, new LivenessSession(iin, steps, expiresAt, false));

        new Thread(() -> {
            try { Thread.sleep(livenessTtlSeconds * 1000L); } catch (InterruptedException ignored) {}
            livenessStore.remove(livenessId);
        }).start();

        return LivenessChallengeResponse.builder()
                .livenessId(livenessId)
                .steps(steps)
                .expiresAt(expiresAt)
                .build();
    }

    @Override
    public LivenessVerifyResponse verifyLiveness(LivenessVerifyRequest request) {
        LivenessSession session = livenessStore.get(request.getLivenessId());

        if (session == null) {
            throw new IllegalStateException("Сессия liveness не найдена или истекла");
        }
        if (session.isUsed()) {
            throw new IllegalStateException("Сессия liveness уже использована");
        }
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            livenessStore.remove(request.getLivenessId());
            throw new IllegalStateException("Сессия liveness истекла");
        }
        if (!session.getIin().equals(request.getIin())) {
            throw new IllegalStateException("ИИН не совпадает с сессией");
        }

        List<LivenessStep> receivedSteps = request.getFrames().stream()
                .map(LivenessVerifyRequest.LivenessFrame::getStep)
                .toList();

        if (!receivedSteps.equals(session.getSteps())) {
            throw new IllegalStateException("Шаги liveness не соответствуют ожидаемым");
        }

        String livenessToken = UUID.randomUUID().toString();

        session.setUsed(true);
        livenessStore.put(request.getLivenessId(), session);

        livenessTokenStore.put(livenessToken,
                new LivenessTokenData(request.getIin(),
                        LocalDateTime.now().plusMinutes(livenessTokenTtlMinutes)));

        new Thread(() -> {
            try { Thread.sleep(livenessTokenTtlMinutes * 60 * 1000L); } catch (InterruptedException ignored) {}
            livenessTokenStore.remove(livenessToken);
        }).start();

        log.info("Liveness verified for iin: {}", request.getIin());

        return LivenessVerifyResponse.builder()
                .success(true)
                .livenessToken(livenessToken)
                .build();
    }

    @Override
    public void enroll(String email, List<Double> descriptor, String livenessToken) {
        validateDescriptor(descriptor);

        LivenessTokenData livenessData = livenessTokenStore.remove(livenessToken);
        if (livenessData == null) {
            throw new IllegalStateException("Liveness токен не найден или уже использован");
        }
        if (livenessData.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Liveness токен истёк");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

        if (!user.getIin().equals(livenessData.getIin())) {
            throw new IllegalStateException("Liveness токен не соответствует пользователю");
        }

        List<UserFaceTemplate> existing = faceTemplateRepository.findByUserAndRevokedAtIsNull(user);
        if (existing.size() >= maxTemplates) {
            throw new IllegalStateException("Достигнут лимит шаблонов лица (" + maxTemplates + ")");
        }

        UserFaceTemplate template = UserFaceTemplate.builder()
                .user(user)
                .descriptor(descriptor)
                .modelName("face-api.js")
                .createdAt(LocalDateTime.now())
                .build();

        faceTemplateRepository.save(template);

        logService.log(
                String.format("Face template enrolled for user %s", email),
                LogLevel.INFO, LogAction.FACE_ENROLL, null, email
        );
    }

    @Override
    public JwtResponse faceLogin(FaceLoginRequest request) {
        validateDescriptor(request.getDescriptor());

        String storedIin = challengeStore.remove(request.getChallengeId());
        if (storedIin == null || !storedIin.equals(request.getIin())) {
            throw new IllegalStateException("Неверный или истёкший challenge");
        }

        LivenessTokenData livenessData = livenessTokenStore.remove(request.getLivenessToken());
        if (livenessData == null) {
            throw new IllegalStateException("Liveness токен не найден или уже использован");
        }
        if (!livenessData.getIin().equals(request.getIin())) {
            throw new IllegalStateException("Liveness токен не соответствует ИИН");
        }
        if (livenessData.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Liveness токен истёк");
        }

        User user = userRepository.findByIin(request.getIin())
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

        if (!user.isActive()) {
            throw new IllegalStateException("Пользователь не активен");
        }

        List<UserFaceTemplate> templates = faceTemplateRepository.findByUserAndRevokedAtIsNull(user);
        if (templates.isEmpty()) {
            throw new IllegalStateException("Face ID не настроен для данного пользователя");
        }

        UserFaceTemplate bestMatch = templates.stream()
                .min(java.util.Comparator.comparingDouble(
                        t -> euclideanDistance(request.getDescriptor(), t.getDescriptor())))
                .orElseThrow(() -> new IllegalStateException("Face ID не настроен для данного пользователя"));

        double minDistance = euclideanDistance(request.getDescriptor(), bestMatch.getDescriptor());

        if (minDistance > threshold) {
            logService.log(
                    String.format("Face login failed for iin %s, distance=%.4f", request.getIin(), minDistance),
                    LogLevel.ERROR, LogAction.FACE_LOGIN_FAILED, null, user.getEmail()
            );
            throw new IllegalStateException("Лицо не распознано");
        }

        bestMatch.setLastUsedAt(LocalDateTime.now());
        faceTemplateRepository.save(bestMatch);

        logService.log(
                String.format("Face login success for iin %s", request.getIin()),
                LogLevel.INFO, LogAction.FACE_LOGIN, null, user.getEmail()
        );

        return JwtResponse.builder()
                .token(jwtTokenUtil.generateTokenFromUsername(user.getEmail()))
                .refreshToken(jwtTokenUtil.generateRefreshToken(user.getEmail()))
                .type("Bearer")
                .faceEnabled(true)
                .username(user.getEmail())
                .build();
    }

    @Override
    @Transactional
    public void removeFace(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

        List<UserFaceTemplate> activeTemplates = faceTemplateRepository.findByUserAndRevokedAtIsNull(user);
        LocalDateTime now = LocalDateTime.now();
        activeTemplates.forEach(t -> t.setRevokedAt(now));
        faceTemplateRepository.saveAll(activeTemplates);

        logService.log(
                String.format("Face templates removed for user %s", email),
                LogLevel.INFO, LogAction.FACE_REMOVE, null, email
        );
    }

    @Override
    public FaceStatusResponse getFaceStatus(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

        List<UserFaceTemplate> templates = faceTemplateRepository.findByUserAndRevokedAtIsNull(user);

        return FaceStatusResponse.builder()
                .enabled(!templates.isEmpty())
                .templatesCount(templates.size())
                .build();
    }


    private void validateDescriptor(List<Double> descriptor) {
        if (descriptor == null || descriptor.size() != descriptorLength) {
            throw new IllegalArgumentException("Дескриптор должен содержать " + descriptorLength + " чисел");
        }
    }

    private double euclideanDistance(List<Double> a, List<Double> b) {
        double sum = 0;
        for (int i = 0; i < a.size(); i++) {
            double diff = a.get(i) - b.get(i);
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}