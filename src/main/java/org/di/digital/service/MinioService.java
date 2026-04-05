package org.di.digital.service;

import org.di.digital.model.CaseFile;
import org.di.digital.model.CaseInterrogationApplicationFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface MinioService {
    CaseFile uploadFile(MultipartFile file, String folder);
    CaseInterrogationApplicationFile uploadApplicationFile(MultipartFile file, String folder, String fio);
    String uploadAudio(MultipartFile file, String folder, String fio);
    String uploadPlan(MultipartFile file, String folder);
    String uploadPlanBytes(byte[] bytes, String folder, String filename);
    String generatePresignedUrlForPreview(String objectPath);
    String generatePresignedUrlForDownload(String objectPath, String fileName);
    void deleteFile(String objectPath);
    void deleteAllFilesFromCase(String caseNumber);
    InputStream downloadFile(String objectPath);

}
