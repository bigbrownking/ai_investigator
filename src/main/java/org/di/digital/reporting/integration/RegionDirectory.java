package org.di.digital.reporting.integration;

import java.util.List;

/**
 * Regions that report, in the fixed row order of consolidated tables.
 */
public interface RegionDirectory {

    List<RegionInfo> orderedRegions();
}
