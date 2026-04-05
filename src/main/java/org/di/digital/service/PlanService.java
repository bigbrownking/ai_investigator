package org.di.digital.service;

import org.di.digital.dto.response.CaseFileResponse;
import org.di.digital.model.CaseFile;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PlanService {
    CaseFileResponse generatePlan(Long caseId, String email);
    CaseFileResponse getPlan(Long caseId);
    boolean hasPlan(Long caseId);
    List<CaseFileResponse> addPlanFiles(Long caseId, List<MultipartFile> files, String email);
    List<CaseFileResponse> getPlanFiles(Long caseId);
}
