package org.di.digital.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.cases.Case;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.service.cases.CaseAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CasePermissionMigrationService {

    private final CaseRepository caseRepository;
    private final CaseAccessService caseAccessService;

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
}