package org.di.digital.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.cases.Case;
import org.di.digital.model.user.User;
import org.di.digital.repository.assess.CaseUserAccessRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.service.cases.CaseAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CasePermissionMigrationService {

    private final CaseRepository caseRepository;
    private final CaseAccessService caseAccessService;
    private final CaseUserAccessRepository caseUserAccessRepository;

    @Transactional
    public int grantFullAccessToAllOwners() {
        List<Case> cases = caseRepository.findAll();
        int migrated = 0;

        for (Case caseEntity : cases) {
            if (caseEntity.getOwner() == null) {
                log.warn("Case {} has no owner, skipping", caseEntity.getNumber());
                continue;
            }
            caseAccessService.grantFullAccess(caseEntity, caseEntity.getOwner());
            migrated++;
        }

        log.info("Permission migration done: {} cases processed", migrated);
        return migrated;
    }

    @Transactional
    public int grantInitialAccessToAllParticipants() {
        List<Case> cases = caseRepository.findAllWithOwnerAndUsers();
        int granted = 0;
        int skipped = 0;

        for (Case caseEntity : cases) {
            Long ownerId = caseEntity.getOwner() != null
                    ? caseEntity.getOwner().getId()
                    : null;

            for (User participant : caseEntity.getUsers()) {
                if(participant.getId().equals(ownerId)){
                    continue;
                }

                if (caseUserAccessRepository.existsByCaseEntityIdAndUserId(
                        caseEntity.getId(), participant.getId())) {
                    skipped++;
                    continue;
                }
                caseAccessService.grantInitialAccess(caseEntity, participant, null);
                granted++;
            }
        }

        log.info("Participant access migration done: {} granted, {} skipped (already had access)",
                granted, skipped);
        return granted;
    }
}