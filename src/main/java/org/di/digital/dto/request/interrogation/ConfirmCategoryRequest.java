package org.di.digital.dto.request.interrogation;

import lombok.Getter;
import lombok.Setter;
import org.di.digital.model.enums.InterrogationSpecialGround;

@Getter
@Setter
public class ConfirmCategoryRequest {
    private InterrogationSpecialGround ground;
    private String groundNote;
}