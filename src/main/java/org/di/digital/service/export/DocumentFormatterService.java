package org.di.digital.service.export;

import java.io.IOException;
import java.util.Map;

public interface DocumentFormatterService {
    byte[] generateQualificationDocument(String text) throws IOException;
    byte[] generateIndictmentDocument(String text) throws IOException;
    byte[] generatePlanDocument(Map<String, Object> plan) throws IOException;
}