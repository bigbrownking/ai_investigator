package org.di.digital.service.impl.face;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.client.FaceAuthClient;
import org.di.digital.dto.response.face.FaceResetResponse;
import org.di.digital.exception.FaceAuthUnavailableException;
import org.di.digital.exception.NotFoundException;
import org.di.digital.model.user.User;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.face.FaceManagementService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceManagementServiceImpl implements FaceManagementService {

    private final UserRepository userRepository;
    private final FaceAuthClient faceAuthClient;

    @Override
    @Transactional
    public FaceResetResponse resetOwnFace(String email) {
        User caller = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден: " + email));
        return doReset(caller);
    }

    @Override
    @Transactional
    public FaceResetResponse resetUserFace(String email, Long targetUserId) {
        User caller = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден: " + email));

        boolean isSelf = caller.getId().equals(targetUserId);
        boolean isAdmin = caller.hasRole("ADMIN");
        if (!isSelf && !isAdmin) {
            throw new AccessDeniedException(
                    "Недостаточно прав для сброса Face ID другого пользователя");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        return doReset(target);
    }

    private FaceResetResponse doReset(User user) {
        Map<String, Object> res;
        try {
            res = faceAuthClient.deleteFace(user.getId());
        } catch (Exception e) {
            log.error("Face reset failed for user {}: {}", user.getId(), e.getMessage());
            throw FaceAuthUnavailableException.of("Не удалось удалить Face ID", e.getMessage());
        }

        user.setFaceEnabled(false);
        userRepository.save(user);

        log.info("Face ID reset for user {} ({})", user.getId(), user.getEmail());
        return FaceResetResponse.builder()
                .deleted(true)
                .userId(user.getId())
                .faceEnabled(false)
                .faceAuth(res == null ? Map.of() : res)
                .build();
    }
}