package org.di.digital.service.export.interrogation;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.di.digital.dto.response.interrogation.CaseInterrogationFullResponse;
import org.di.digital.model.user.User;

public interface InterrogationProtocolBuilder {
    void build(XWPFDocument doc, CaseInterrogationFullResponse data, User user);
}