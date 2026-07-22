package org.di.digital.service.osmotr;

import org.di.digital.dto.request.osmotr.DistributionRequest;
import org.di.digital.dto.response.osmotr.OsmotrResultDto;
import org.di.digital.dto.response.osmotr.OsmotrResultSegmentDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface OsmotrService {
    OsmotrResultDto submitDocument(String caseNumber, String userEmail, MultipartFile file) throws Exception;
    List<OsmotrResultDto> getResultsByCaseNumber(String caseNumber, String email);
    Optional<OsmotrResultDto> getResult(String caseNumber, Long resultId, String email);
    OsmotrResultDto updateDistribution(String caseNumber, Long resultId,
                                       DistributionRequest request, String email);
    List<OsmotrResultDto> searchSegments(String caseNumber, String query, String email);
    byte[] downloadSegment(String caseNumber, Long resultId, Long segmentId, String email);
    byte[] mergeSegments(String caseNumber, Long resultId, String type, String email) throws Exception;
    byte[] downloadGeneratedFile(String caseNumber, Long resultId, String fileType, String email);
}
