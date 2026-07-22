package org.di.digital.service.export;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface DocumentFormatterService {
    byte[] generateQualificationDocument(List<Map<String, Object>> sections) throws IOException;
    byte[] generateIndictmentDocument(List<Map<String, Object>> sections) throws IOException;
    byte[] generatePlanDocument(Map<String, Object> plan, String approvedBy) throws IOException;
}