package org.di.digital.service.export;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentFormatterServiceImpl implements DocumentFormatterService {

    private final QualificationDocumentFormatter qualificationFormatter;
    private final IndictmentDocumentFormatter indictmentFormatter;
    private final PlanDocumentFormatter planDocumentFormatter;

    @Override
    public byte[] generateQualificationDocument(List<Map<String, Object>> sections) throws IOException {
        return qualificationFormatter.generate(sections);
    }

    @Override
    public byte[] generateIndictmentDocument(List<Map<String, Object>> sections) throws IOException {
        return indictmentFormatter.generate(sections);
    }

    @Override
    public byte[] generatePlanDocument(Map<String, Object> plan, String approvedBy) throws IOException {
        return planDocumentFormatter.generate(plan, approvedBy);
    }
}
