package org.di.digital.service.impl.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.user.User;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.cases.CaseAccessService;
import org.di.digital.util.requests.UserUtil;
import org.springframework.stereotype.Service;

import static org.di.digital.util.requests.UserUtil.getCurrentLang;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterrogationAuthService {

    private final CaseInterrogationRepository caseInterrogationRepository;
    private final UserRepository userRepository;
    private final CaseAccessService caseAccessService;
    private final UserUtil userUtil;

    public record AuthorizedInterrogation(CaseInterrogation interrogation, User user) {}

    public AuthorizedInterrogation loadAndAuthorize(Long caseId, Long interrogationId,
                                                    String email,
                                                    CaseModule module, CaseAction action) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.INTERROGATION.localized(getCurrentLang(), interrogationId.toString())));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(getCurrentLang(), email)));

        userUtil.validateUserAccess(interrogation.getCaseEntity(), user);
        caseAccessService.require(interrogation.getCaseEntity(), user, module, action);

        if (!interrogation.getCaseEntity().getId().equals(caseId)) {
            throw new IllegalStateException("Допрос не принадлежит делу: " + caseId);
        }
        return new AuthorizedInterrogation(interrogation, user);
    }
}
