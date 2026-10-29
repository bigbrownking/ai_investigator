package org.di.digital.util.mapper;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.response.cases.ReferenceDto;
import org.di.digital.dto.response.chat.CaseChatMessageDto;
import org.di.digital.dto.response.chat.ReferenceLinkDto;
import org.di.digital.model.cases.CaseChatMessage;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.util.file.CaseFileNameIndex;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

@Component
@RequiredArgsConstructor
public class MessageMapper {

    private final FileUrlResolver fileUrls;

    // ---------------- сообщения чата ----------------

    public List<CaseChatMessageDto> toDtoList(Collection<CaseChatMessage> messages,
                                              Collection<CaseFile> caseFiles) {
        return toDtoList(messages, caseFiles, f -> true);
    }

    public List<CaseChatMessageDto> toDtoList(Collection<CaseChatMessage> messages,
                                              Collection<CaseFile> caseFiles,
                                              Predicate<CaseFile> canRead) {
        if (messages == null) return List.of();
        Context ctx = context(caseFiles, canRead);
        return messages.stream()
                .filter(Objects::nonNull)
                .map(m -> toDto(m, ctx))
                .toList();
    }

    public CaseChatMessageDto toDto(CaseChatMessage message, Collection<CaseFile> caseFiles) {
        return toDto(message, context(caseFiles, f -> true));
    }

    // ---------------- ссылки из ReferenceDto (чаты) ----------------

    public List<ReferenceLinkDto> toLinks(Collection<ReferenceDto> refs,
                                          Collection<CaseFile> caseFiles) {
        return toLinks(refs, context(caseFiles, f -> true));
    }

    // ---------------- ссылки из ReferenceLinkDto (ответы Python) ----------------

    public List<ReferenceLinkDto> enrichLinks(Collection<ReferenceLinkDto> refs,
                                              Collection<CaseFile> caseFiles) {
        return enrichLinks(refs, context(caseFiles, f -> true));
    }

    /** Для многих списков подряд (секции акта): один индекс и один токен на файл за запрос. */
    public Function<Collection<ReferenceLinkDto>, List<ReferenceLinkDto>> linkEnricher(
            Collection<CaseFile> caseFiles) {
        return linkEnricher(caseFiles, f -> true);
    }

    public Function<Collection<ReferenceLinkDto>, List<ReferenceLinkDto>> linkEnricher(
            Collection<CaseFile> caseFiles, Predicate<CaseFile> canRead) {
        Context ctx = context(caseFiles, canRead);
        return refs -> enrichLinks(refs, ctx);
    }

    // ---------------- internal ----------------

    private CaseChatMessageDto toDto(CaseChatMessage message, Context ctx) {
        return CaseChatMessageDto.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .edited(message.getIsEdited())
                .selected(message.getIsSelected())
                .createdDate(message.getCreatedDate())
                .complete(message.isComplete())
                .references(toLinks(message.getReferences(), ctx))
                .build();
    }

    private List<ReferenceLinkDto> toLinks(Collection<ReferenceDto> refs, Context ctx) {
        if (refs == null) return List.of();
        return refs.stream()
                .filter(Objects::nonNull)
                .map(r -> resolve(r.getReferenceId(), r.getFilePath(), r.getOpis(), ctx))
                .toList();
    }

    private List<ReferenceLinkDto> enrichLinks(Collection<ReferenceLinkDto> refs, Context ctx) {
        if (refs == null) return List.of();
        return refs.stream()
                .filter(Objects::nonNull)
                .map(r -> resolve(r.getReferenceId(), r.getLink(), r.getOpis(), ctx))
                .toList();
    }

    /** Всегда создаёт новый объект, исходные данные не изменяются. */
    private ReferenceLinkDto resolve(String referenceId, String fileName, String opis, Context ctx) {
        ReferenceLinkDto.ReferenceLinkDtoBuilder b = ReferenceLinkDto.builder()
                .referenceId(referenceId)
                .opis(opis);

        Optional<CaseFile> file = ctx.index.find(fileName);
        if (file.isEmpty()) {
            // файл не из дела: показываем имя без ссылки
            return b.name(fileName).build();
        }

        CaseFile f = file.get();
        b.fileId(f.getId()).name(f.getOriginalFileName());

        if (ctx.canRead.test(f)) {
            b.link(ctx.previewUrls.computeIfAbsent(f.getId(),
                    id -> fileUrls.preview(f.getFileUrl(), f.getOriginalFileName(), f.getContentType())));
        }
        return b.build();
    }

    private static Context context(Collection<CaseFile> caseFiles, Predicate<CaseFile> canRead) {
        return new Context(CaseFileNameIndex.of(caseFiles), canRead);
    }

    private static final class Context {
        final CaseFileNameIndex index;
        final Predicate<CaseFile> canRead;
        final Map<Long, String> previewUrls = new HashMap<>();

        Context(CaseFileNameIndex index, Predicate<CaseFile> canRead) {
            this.index = index;
            this.canRead = canRead;
        }
    }
}