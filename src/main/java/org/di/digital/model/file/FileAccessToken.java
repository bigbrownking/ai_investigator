package org.di.digital.model.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileAccessToken implements Serializable {

    private String objectName;
    private String originalFileName;
    private String contentType;
    private boolean inline;
}