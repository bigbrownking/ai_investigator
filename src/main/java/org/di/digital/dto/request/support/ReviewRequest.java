package org.di.digital.dto.request.support;

import lombok.Data;
import java.util.List;

@Data
public class ReviewRequest {
    private String subject;
    private List<ReviewItemRequest> items;
}