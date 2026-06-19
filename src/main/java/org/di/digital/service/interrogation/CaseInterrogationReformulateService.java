package org.di.digital.service.interrogation;

import org.di.digital.dto.request.interrogation.CleanTranscriptRequest;
import org.di.digital.dto.request.interrogation.ReformulateQuestionRequest;
import org.di.digital.dto.response.interrogation.CleanTranscriptResponse;
import org.di.digital.dto.response.interrogation.ReformulateQuestionResponse;

public interface CaseInterrogationReformulateService {
    ReformulateQuestionResponse reformulateQuestion(ReformulateQuestionRequest request);
    CleanTranscriptResponse cleanTranscript(CleanTranscriptRequest request);

}
