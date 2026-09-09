package org.di.digital.dto.request.cases.access;

import lombok.Getter;
import lombok.Setter;
import org.di.digital.dto.response.access.FileGrantDto;

import java.util.List;

@Getter
@Setter
public class UpdateFileAccessRequest {
    private List<FileGrantDto> fileGrants;
}
