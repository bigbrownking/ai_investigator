package org.di.digital.dto.request.interrogation;

import lombok.Data;
import java.util.Map;

@Data
public class ApplicationFileUploadRequest {
    private Map<String, String> displayNames;
}