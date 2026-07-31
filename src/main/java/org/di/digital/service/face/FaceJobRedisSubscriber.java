package org.di.digital.service.face;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.face.FaceJobEvent;
import org.di.digital.model.user.User;
import org.di.digital.repository.user.UserRepository;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Subscribes to FaceAuth's "face-job-events" Redis channel and forwards each
 * event to the target user over the orchestrator's existing WebSocket.
 *
 * Authenticated jobs (userId set) -> push to /user/{email}/queue/face-jobs.
 * Anonymous jobs (jobToken only)  -> the client polls with jobToken; we cannot
 * map it to an email, so we skip WS push for those (registration flow polls).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaceJobRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;       // orchestrator is Jackson 2
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            FaceJobEvent event = objectMapper.readValue(message.getBody(), FaceJobEvent.class);

            if (event.userId() == null) {
                // anonymous registration job -> client polls with jobToken, no WS mapping
                log.debug("Anonymous face job event, skipping WS push");
                return;
            }

            User user = userRepository.findById(event.userId()).orElse(null);
            if (user == null) {
                log.warn("Face job event for unknown userId={}", event.userId());
                return;
            }

            messagingTemplate.convertAndSendToUser(
                    user.getEmail(), "/queue/face-jobs", event.job());
            log.debug("Forwarded face job to WS user {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to handle face job event: {}", e.getMessage(), e);
        }
    }
}