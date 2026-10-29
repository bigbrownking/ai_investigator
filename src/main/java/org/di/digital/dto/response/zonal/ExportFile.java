package org.di.digital.dto.response.zonal;

public record ExportFile(String fileName, String contentType, byte[] content) {
}