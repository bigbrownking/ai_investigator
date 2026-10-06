package org.di.digital.reporting.service;

import org.di.digital.reporting.dto.response.ConsolidatedTableResponse;

import java.time.LocalDate;

/**
 * Main admin side: the consolidated table is built on every request from the latest rows,
 * so the "Refresh" button is a repeated GET and nothing is stored.
 */
public interface ConsolidationService {

    /** @param reportDate today when null */
    ConsolidatedTableResponse getConsolidated(String templateCode, LocalDate reportDate);
}
