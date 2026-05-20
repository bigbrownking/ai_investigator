package org.di.digital.dto.request;

import lombok.Data;
import java.util.Map;

@Data
public class ApplicationFileUploadRequest {
    private Map<String, String> displayNames;
}