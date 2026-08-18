package org.di.digital.util.mapper;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.response.interrogation.*;
import org.di.digital.model.interrogation.CaseInterrogationProtocol;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class InterrogationProtocolMapper {

    private final LocalizationHelper localizationHelper;

    public CaseInterrogationProtocolResponse toResponse(CaseInterrogationProtocol p) {
        String citizenship = localizationHelper.toTitleCase(p.getCitizenship());

        return CaseInterrogationProtocolResponse.builder()
                .fio(localizationHelper.toTitleCase(p.getFio()))
                .dateOfBirth(localizationHelper.formatToRussianDate(p.getDateOfBirth()))
                .birthPlace(localizationHelper.toTitleCase(p.getBirthPlace()))
                .citizenship(citizenship != null ? "гражданин Республики " + citizenship : null)
                .nationality(localizationHelper.toTitleCase(p.getNationality()))
                .educations(mapEducations(p))
                .martialStatus(formatMartialStatus(p))
                .workOrStudyPlace(p.getWorkOrStudyPlace())
                .position(p.getPosition())
                .address(p.getAddress())
                .contactPhone(p.getContactPhone())
                .contactEmail(p.getContactEmail())
                .other(p.getOther())
                .relation(mapRelations(p))
                .technical(p.getTechnical())
                .military(mapMilitaries(p))
                .criminalRecord(mapCriminals(p))
                .iinOrPassport(p.getIinOrPassport())
                .interrogationId(p.getInterrogation() != null ? p.getInterrogation().getId() : null)
                .build();
    }

    private List<EducationResponse> mapEducations(CaseInterrogationProtocol p) {
        return map(p.getEducations(), e -> EducationResponse.builder()
                .id(e.getId()).type(e.getType()).about(e.getAbout()).build());
    }

    private List<CriminalResponse> mapCriminals(CaseInterrogationProtocol p) {
        return map(p.getCriminals(), e -> CriminalResponse.builder()
                .id(e.getId()).type(e.getType()).about(e.getAbout()).build());
    }

    private List<MilitaryResponse> mapMilitaries(CaseInterrogationProtocol p) {
        return map(p.getMilitaries(), e -> MilitaryResponse.builder()
                .id(e.getId()).type(e.getType()).about(e.getAbout()).build());
    }

    private List<RelationResponse> mapRelations(CaseInterrogationProtocol p) {
        return map(p.getRelationRecords(), e -> RelationResponse.builder()
                .id(e.getId()).type(e.getType()).about(e.getAbout()).build());
    }

    private <S, T> List<T> map(List<S> source, Function<S, T> mapper) {
        return source != null ? source.stream().map(mapper).toList() : List.of();
    }

    private String formatMartialStatus(CaseInterrogationProtocol p) {
        String status = p.getMartialStatus();
        if (status == null) return null;

        String s = status.toLowerCase().trim();
        boolean isFemale = "2".equals(p.getSexId());

        if (s.contains("холост") || s.contains("не замужем")) return isFemale ? "не замужем" : "холост";
        if (s.contains("женат") || s.contains("замужем")) return isFemale ? "замужем" : "женат";
        if (s.contains("разведен")) return isFemale ? "разведена" : "разведен";
        if (s.contains("вдовец") || s.contains("вдова")) return isFemale ? "вдова" : "вдовец";
        return status;
    }
}