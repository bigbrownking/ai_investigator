package org.di.digital.dto.request.cases;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.di.digital.dto.response.access.FileGrantDto;

import java.util.List;

@Getter
@Setter
public class AddSogToCaseRequest {
    @NotNull
    private Long id;
    private List<FileGrantDto> fileGrants;
}
