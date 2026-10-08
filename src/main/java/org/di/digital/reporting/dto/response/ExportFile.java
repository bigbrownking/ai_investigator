package org.di.digital.reporting.dto.response;

public record ExportFile(String fileName, String contentType, byte[] content) {
}