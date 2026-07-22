package org.di.digital.service.core;

import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.interrogation.CaseInterrogationApplicationFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface MinioService {
    CaseFile uploadFile(MultipartFile file, String folder);
    CaseInterrogationApplicationFile uploadApplicationFile(MultipartFile file, String folder, String fio);
    String uploadAudio(MultipartFile file, String folder, String fio);
    String generatePresignedUrlForPreview(String objectPath);
    String generatePresignedUrlForDownload(String objectPath, String fileName);
    void deleteFile(String objectPath);
    void deleteAllFilesFromCase(String caseNumber);
    InputStream downloadFile(String objectPath);
    String uploadOsmotrFile(byte[] bytes, String caseNumber, String fileName, String subfolder);
    String uploadOsmotrGeneratedFile(byte[] bytes, String caseNumber, String fileName, String subfolder);
    boolean fileExists(String objectPath);
}
