package org.di.digital.service.export.interrogation;

import org.di.digital.dto.response.CaseInterrogationFullResponse;

public interface InterrogationExportService {
    byte[] exportToDocx(CaseInterrogationFullResponse data);
}
