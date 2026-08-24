package org.di.digital.dto.response.cases;

import org.di.digital.model.enums.cases.CaseRejectionReason;
import java.time.LocalDateTime;

public interface RejectionEnrichable {
    Long getId();
    void setRejectionReason(String reason);
    void setRejectionByFio(String fio);
    void setRejectionAt(LocalDateTime at);
    String getOwnerFio();
    void setOwnerFio(String fio);
}