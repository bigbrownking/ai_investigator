package org.di.digital.service.export;

import org.di.digital.dto.response.CaseInterrogationFullResponse;

public interface InterrogationExportService {
    byte[] exportToDocx(CaseInterrogationFullResponse data);
}
