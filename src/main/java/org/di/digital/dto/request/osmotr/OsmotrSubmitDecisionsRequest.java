package org.di.digital.dto.request.osmotr;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.di.digital.dto.response.osmotr.OsmotrDataItemDto;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsmotrSubmitDecisionsRequest {
    @JsonProperty("session_id")
    private String sessionId;

    private Map<String, Boolean> decisions;
}