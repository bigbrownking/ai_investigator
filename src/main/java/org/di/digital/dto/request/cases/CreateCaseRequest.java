package org.di.digital.dto.request.cases;

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
    private List<MultipartFile> files;
}
