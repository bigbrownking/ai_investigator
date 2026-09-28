package org.di.digital.reporting.integration;

import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.user.Region;
import org.di.digital.repository.user.RegionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads regions from the Postgres regions table and puts them in the fixed row order of consolidated tables.
 * <p>
 * The table has no ordering column, and ru_name/kz_name may hold full department names
 * ("Департамент ... по городу Астане", "Астана қаласы бойынша ..."), so regions are matched by name
 * fragments rather than exact names. reporting.region-order lists one entry per row, entries separated
 * by ';'. An entry lists fragments separated by '|': a region matches when its ru or kz name contains
 * any plain fragment and none of the fragments prefixed with '!'. Matching ignores case.
 * Regions no entry matched follow at the end, alphabetically by ru_name.
 */
@Slf4j
@Component
public class PostgresRegionDirectory implements RegionDirectory {

    /**
     * Cities of republican significance first, then oblasts alphabetically (ru). Fragments are stems,
     * so declensions match ("Астана", "Астане", "Астаны"); Kazakh spellings are listed next to Russian ones.
     * Compile-time constant: it is used inside the @Value placeholder below.
     */
    static final String DEFAULT_ORDER = "астан;"
            + "алматы|!облыс|!область|!алматинск;"     // city of Almaty, not Almaty oblast
            + "шымкент;"
            + "абай;"
            + "акмол|ақмола;"
            + "актюб|актобе|ақтөбе;"
            + "алматинск|алматы облыс;"
            + "атырау;"
            + "восточно|шығыс;"
            + "жамбыл;"
            + "жетыс|жетіс;"
            + "западно|батыс;"
            + "караганд|қарағанды;"
            + "костанай|қостанай;"
            + "кызылорд|қызылорда;"
            + "мангист|мангыст|маңғыстау;"
            + "павлодар;"
            + "северо|солтүстік;"
            + "туркестан|түркістан;"
            + "улытау|ұлытау";

    private final RegionRepository regionRepository;
    private final List<Entry> entries;
    // Order problems are logged once per distinct set of problems, not on every request
    private final AtomicReference<List<String>> reportedProblems = new AtomicReference<>(List.of());

    public PostgresRegionDirectory(RegionRepository regionRepository,
                                   @Value("${reporting.region-order:" + DEFAULT_ORDER + "}") String regionOrder) {
        this.regionRepository = regionRepository;
        this.entries = Arrays.stream(regionOrder.split(";"))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(Entry::parse)
                .toList();
    }

    @Override
    public List<RegionInfo> orderedRegions() {
        List<Region> regions = regionRepository.findAllByOrderByRuNameAsc();
        List<RegionInfo> result = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        List<String> problems = new ArrayList<>();

        for (Entry entry : entries) {
            List<Region> candidates = regions.stream()
                    .filter(region -> !used.contains(region.getId()) && entry.matches(region))
                    .toList();
            if (candidates.isEmpty()) {
                problems.add("no region matches '" + entry.source() + "'");
                continue;
            }
            if (candidates.size() > 1) {
                problems.add("'" + entry.source() + "' matches several regions " + names(candidates)
                        + ", placed " + candidates.get(0).getRuName());
            }
            Region region = candidates.get(0);
            used.add(region.getId());
            result.add(toInfo(region));
        }

        List<Region> rest = regions.stream().filter(region -> !used.contains(region.getId())).toList();
        if (!rest.isEmpty()) {
            problems.add("regions not in reporting.region-order, placed last: " + names(rest));
        }
        rest.forEach(region -> result.add(toInfo(region)));

        reportOnce(problems);
        return result;
    }

    private void reportOnce(List<String> problems) {
        if (!problems.isEmpty() && !problems.equals(reportedProblems.getAndSet(problems))) {
            problems.forEach(problem -> log.warn("Reporting region order: {}", problem));
        }
    }

    private static List<String> names(List<Region> regions) {
        return regions.stream().map(Region::getRuName).toList();
    }

    private static RegionInfo toInfo(Region region) {
        return new RegionInfo(region.getId(), region.getRuName(), region.getKzName());
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private record Entry(String source, List<String> include, List<String> exclude) {

        static Entry parse(String source) {
            List<String> include = new ArrayList<>();
            List<String> exclude = new ArrayList<>();
            for (String fragment : source.split("\\|")) {
                String value = normalize(fragment.trim());
                if (value.startsWith("!") && value.length() > 1) {
                    exclude.add(value.substring(1).trim());
                } else if (!value.isEmpty()) {
                    include.add(value);
                }
            }
            return new Entry(source, include, exclude);
        }

        boolean matches(Region region) {
            String names = normalize(region.getRuName()) + " | " + normalize(region.getKzName());
            return include.stream().anyMatch(names::contains) && exclude.stream().noneMatch(names::contains);
        }
    }
}
