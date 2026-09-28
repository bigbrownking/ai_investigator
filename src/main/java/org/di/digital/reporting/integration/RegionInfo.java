package org.di.digital.reporting.integration;

/**
 * Region as seen by the reporting module: id and display names, no JPA entity.
 */
public record RegionInfo(Long id, String nameRu, String nameKz) {
}
