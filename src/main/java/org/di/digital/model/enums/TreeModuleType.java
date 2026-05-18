package org.di.digital.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TreeModuleType {

    CASE_INFO("case-info", "/workspaces/{case_id}/case-info"),
    SUSPECTS("suspects", "/workspaces/{case_id}/suspects"),
    VICTIMS("victims", "/workspaces/{case_id}/victims"),
    CRIMES("crimes", "/workspaces/{case_id}/crimes"),
    EVIDENCE("evidence", "/workspaces/{case_id}/evidence"),
    PERSONS("persons", "/workspaces/{case_id}/persons"),
    FINANCIAL("financial", "/workspaces/{case_id}/financial");

    private final String moduleName;
    private final String endpoint;

    public String getEndpoint(String caseId) {
        return endpoint.replace("{case_id}", caseId);
    }

    public static TreeModuleType fromModuleName(String moduleName) {
        for (TreeModuleType type : values()) {
            if (type.getModuleName().equalsIgnoreCase(moduleName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown module name: " + moduleName);
    }
}
