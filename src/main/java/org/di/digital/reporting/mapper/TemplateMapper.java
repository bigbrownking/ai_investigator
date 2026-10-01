package org.di.digital.reporting.mapper;

import org.di.digital.reporting.dto.ColumnDefinitionDto;
import org.di.digital.reporting.dto.SelectOptionDto;
import org.di.digital.reporting.dto.response.ActiveFormResponse;
import org.di.digital.reporting.dto.response.TemplateResponse;
import org.di.digital.reporting.model.ColumnDefinition;
import org.di.digital.reporting.model.ReportTemplate;
import org.di.digital.reporting.model.SelectOption;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TemplateMapper {

    public TemplateResponse toResponse(ReportTemplate template) {
        return TemplateResponse.builder()
                .id(template.getId())
                .code(template.getCode())
                .version(template.getVersion())
                .name(template.getName())
                .description(template.getDescription())
                .status(template.getStatus())
                .columns(toColumnDtos(template.getColumns()))
                .createdBy(template.getCreatedBy())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .publishedAt(template.getPublishedAt())
                .archivedAt(template.getArchivedAt())
                .lockVersion(template.getLockVersion())
                .build();
    }

    public ActiveFormResponse toActiveForm(ReportTemplate template) {
        return ActiveFormResponse.builder()
                .code(template.getCode())
                .name(template.getName())
                .description(template.getDescription())
                .version(template.getVersion())
                .publishedAt(template.getPublishedAt())
                .build();
    }

    public List<ColumnDefinitionDto> toColumnDtos(List<ColumnDefinition> columns) {
        return columns.stream()
                .map(column -> ColumnDefinitionDto.builder()
                        .key(column.getKey())
                        .label(column.getLabel())
                        .type(column.getType())
                        .required(column.isRequired())
                        .options(column.getOptions() == null ? new ArrayList<>() : column.getOptions().stream()
                                .map(o -> SelectOptionDto.builder().value(o.getValue()).label(o.getLabel()).build())
                                .collect(Collectors.toCollection(ArrayList::new)))
                        .order(column.getOrder())
                        .build())
                .toList();
    }

    /**
     * Sorts by explicit order (list position when absent) and renumbers 1..n.
     * Keys and labels are trimmed; null keys/labels are kept for the validator to report.
     */
    public List<ColumnDefinition> toColumns(List<ColumnDefinitionDto> dtos) {
        if (dtos == null) {
            return new ArrayList<>();
        }
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < dtos.size(); i++) {
            indexes.add(i);
        }
        indexes.sort(Comparator.comparingInt(i -> {
            ColumnDefinitionDto dto = dtos.get(i);
            return dto != null && dto.getOrder() != null ? dto.getOrder() : i;
        }));

        List<ColumnDefinition> columns = new ArrayList<>();
        for (Integer i : indexes) {
            ColumnDefinitionDto dto = dtos.get(i);
            if (dto == null) {
                continue;
            }
            columns.add(ColumnDefinition.builder()
                    .key(trim(dto.getKey()))
                    .label(trim(dto.getLabel()))
                    .type(dto.getType())
                    .required(dto.isRequired())
                    .options(dto.getOptions() == null ? new ArrayList<>() : dto.getOptions().stream()
                            .filter(o -> o != null)
                            .map(o -> SelectOption.builder().value(trim(o.getValue())).label(trim(o.getLabel())).build())
                            .collect(Collectors.toCollection(ArrayList::new)))
                    .order(columns.size() + 1)
                    .build());
        }
        return columns;
    }

    public List<ColumnDefinition> copyColumns(List<ColumnDefinition> columns) {
        return columns.stream()
                .map(c -> ColumnDefinition.builder()
                        .key(c.getKey())
                        .label(c.getLabel())
                        .type(c.getType())
                        .required(c.isRequired())
                        .options(c.getOptions() == null ? new ArrayList<>() : c.getOptions().stream()
                                .map(o -> SelectOption.builder().value(o.getValue()).label(o.getLabel()).build())
                                .collect(Collectors.toCollection(ArrayList::new)))
                        .order(c.getOrder())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
