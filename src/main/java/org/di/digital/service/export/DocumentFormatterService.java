package org.di.digital.service.export;

import java.io.IOException;

public interface DocumentFormatterService {
    byte[] generateQualificationDocument(String text) throws IOException;
    byte[] generateIndictmentDocument(String text) throws IOException;
}