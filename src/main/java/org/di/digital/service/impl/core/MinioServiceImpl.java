package org.di.digital.service.impl.core;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.model.interrogation.CaseInterrogationApplicationFile;
import org.di.digital.service.core.MinioObjectStorage;
import org.di.digital.service.core.MinioService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioServiceImpl implements MinioService {

    private final MinioObjectStorage storage;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "xls", "xlsx");

    @Override
    public CaseFile uploadFile(MultipartFile file, String folder) {
        return uploadFile(file, folder, true);
    }

    @Override
    public CaseFile uploadFile(MultipartFile file, String folder, boolean validateType) {
        if (validateType) {
            validateFileType(file.getOriginalFilename());
        }

        String storedFileName = generateFileName(file.getOriginalFilename());
        String objectName = folder + "/" + storedFileName;

        String objectPath;
        try (InputStream inputStream = file.getInputStream()) {
            objectPath = storage.putObject(objectName, inputStream, file.getSize(), file.getContentType());
        } catch (Exception e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to upload file", e);
        }

        return CaseFile.builder()
                .originalFileName(file.getOriginalFilename())
                .storedFileName(storedFileName)
                .fileUrl(objectPath)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .uploadedAt(LocalDateTime.now())
                .status(CaseFileStatusEnum.UPLOADED)
                .build();
    }

    @Override
    public CaseInterrogationApplicationFile uploadApplicationFile(MultipartFile file, String folder, String fio) {
        String storedFileName = generateFileName(file.getOriginalFilename());
        String objectName = folder + "/application/" + fio + "/" + storedFileName;

        String objectPath;
        try (InputStream inputStream = file.getInputStream()) {
            objectPath = storage.putObject(objectName, inputStream, file.getSize(), file.getContentType());
        } catch (Exception e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to upload file", e);
        }

        return CaseInterrogationApplicationFile.builder()
                .originalFileName(file.getOriginalFilename())
                .storedFileName(storedFileName)
                .fileUrl(objectPath)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .uploadedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public String uploadAudio(MultipartFile file, String folder, String fio) {
        String storedFileName = generateFileName(file.getOriginalFilename());
        String objectName = folder + "/audio/" + fio + "/" + storedFileName;

        String objectPath;
        try (InputStream inputStream = file.getInputStream()) {
            objectPath = storage.putObject(objectName, inputStream, file.getSize(), file.getContentType());
        } catch (Exception e) {
            log.error("Error uploading audio file: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to upload audio file", e);
        }

        log.info("Audio uploaded successfully for interrogation: {}, path: {}", folder, objectPath);
        return objectPath;
    }

    @Override
    public String generatePresignedUrlForPreview(String objectPath) {
        String objectName = storage.extractObjectNameFromPath(objectPath);
        Map<String, String> headers = new HashMap<>();
        headers.put("response-content-disposition", "inline");
        return storage.presignedGetUrl(objectName, headers);
    }

    @Override
    public String generatePresignedUrlForDownload(String objectPath, String fileName) {
        String objectName = storage.extractObjectNameFromPath(objectPath);
        Map<String, String> headers = new HashMap<>();
        headers.put("response-content-disposition", "attachment; filename=\"" + fileName + "\"");
        return storage.presignedGetUrl(objectName, headers);
    }

    private String generateFileName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID() + extension;
    }

    @Override
    public void deleteFile(String objectPath) {
        storage.removeObject(storage.extractObjectNameFromPath(objectPath));
    }

    @Override
    public void deleteAllFilesFromCase(String caseNumber) {
        List<String> names = storage.listObjectNames(caseNumber + "/");
        storage.removeObjects(names);
        log.info("Deleted ALL {} files for case: {}", names.size(), caseNumber);
    }

    @Override
    public InputStream downloadFile(String objectPath) {
        return storage.getObject(storage.extractObjectNameFromPath(objectPath));
    }

    @Override
    public String uploadOsmotrFile(byte[] bytes, String caseNumber, String fileName, String subfolder) {
        validatePdfOnly(fileName);
        String objectName = String.format("%s/osmotr/%s/%s", caseNumber, subfolder, fileName);
        return storage.putObject(objectName, new ByteArrayInputStream(bytes), bytes.length, "application/pdf");
    }

    @Override
    public String uploadOsmotrGeneratedFile(byte[] bytes, String caseNumber, String fileName, String subfolder) {
        String objectName = String.format("%s/osmotr/%s/%s", caseNumber, subfolder, fileName);
        return storage.putObject(objectName, new ByteArrayInputStream(bytes), bytes.length,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Override
    public boolean fileExists(String objectPath) {
        return storage.exists(storage.extractObjectNameFromPath(objectPath));
    }

    private void validateFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new IllegalStateException("Файл должен иметь расширение: " + fileName);
        }
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalStateException(
                    "Недопустимый тип файла: " + fileName +
                            ". Разрешены: pdf, doc, docx, xls, xlsx");
        }
    }

    private void validatePdfOnly(String fileName) {
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            throw new IllegalStateException("Осмотр поддерживает только PDF файлы: " + fileName);
        }
    }
}