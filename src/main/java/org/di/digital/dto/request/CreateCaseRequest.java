package org.di.digital.dto.request;

import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCaseRequest {
    private String title;
    private String number;
    private String description;
    private List<MultipartFile> files;
}
