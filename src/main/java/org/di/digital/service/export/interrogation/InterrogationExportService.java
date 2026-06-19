package org.di.digital.service.export.interrogation;

import org.di.digital.dto.response.interrogation.CaseInterrogationFullResponse;
import org.di.digital.model.user.User;

public interface InterrogationExportService {
    byte[] exportToDocx(CaseInterrogationFullResponse data, User user);
}
