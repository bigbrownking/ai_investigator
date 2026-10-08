package org.di.digital.reporting.mapper;

import org.di.digital.reporting.dto.response.ColumnDefinitionDto;
import org.di.digital.reporting.dto.response.SelectOptionDto;
import org.di.digital.reporting.model.ColumnDefinition;
import org.di.digital.reporting.model.SelectOption;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TemplateMapper {

    public List<ColumnDefinitionDto> toColumnDtos(List<ColumnDefinition> columns) {
        return columns.stream()
                .<ColumnDefinitionDto>map(column -> ColumnDefinitionDto.builder()
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
}
