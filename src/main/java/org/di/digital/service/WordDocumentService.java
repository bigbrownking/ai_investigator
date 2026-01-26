package org.di.digital.service;

import java.io.IOException;

public interface WordDocumentService {
    byte[] generateQualificationDocument(String text) throws IOException;
    byte[] generateIndictmentDocument(String text) throws IOException;
}