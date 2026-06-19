package org.di.digital.service.export;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentFormatterServiceImpl implements DocumentFormatterService {

    private final QualificationDocumentFormatter qualificationFormatter;
    private final IndictmentDocumentFormatter indictmentFormatter;
    private final PlanDocumentFormatter planDocumentFormatter;

    @Override
    public byte[] generateQualificationDocument(String text) throws IOException {
        return qualificationFormatter.generate(text);
    }

    @Override
    public byte[] generateIndictmentDocument(String text) throws IOException {
        return indictmentFormatter.generate(text);
    }

    @Override
    public byte[] generatePlanDocument(Map<String, Object> plan) throws IOException {
        return planDocumentFormatter.generate(plan);
    }
}
