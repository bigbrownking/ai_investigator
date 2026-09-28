package org.di.digital.reporting.integration;

import org.di.digital.model.user.Region;
import org.di.digital.repository.user.RegionRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostgresRegionDirectoryTest {

    /** Russian and Kazakh names of all 20 regions in the expected row order. */
    private static final String[][] SHORT_NAMES = {
            {"город Астана", "Астана қаласы"},
            {"город Алматы", "Алматы қаласы"},
            {"город Шымкент", "Шымкент қаласы"},
            {"Абайская область", "Абай облысы"},
            {"Акмолинская область", "Ақмола облысы"},
            {"Актюбинская область", "Ақтөбе облысы"},
            {"Алматинская область", "Алматы облысы"},
            {"Атырауская область", "Атырау облысы"},
            {"Восточно-Казахстанская область", "Шығыс Қазақстан облысы"},
            {"Жамбылская область", "Жамбыл облысы"},
            {"Жетысуская область", "Жетісу облысы"},
            {"Западно-Казахстанская область", "Батыс Қазақстан облысы"},
            {"Карагандинская область", "Қарағанды облысы"},
            {"Костанайская область", "Қостанай облысы"},
            {"Кызылординская область", "Қызылорда облысы"},
            {"Мангистауская область", "Маңғыстау облысы"},
            {"Павлодарская область", "Павлодар облысы"},
            {"Северо-Казахстанская область", "Солтүстік Қазақстан облысы"},
            {"Туркестанская область", "Түркістан облысы"},
            {"Улытауская область", "Ұлытау облысы"},
    };

    @Test
    void ordersAllTwentyRegionsWithAstanaFirst() {
        List<Region> regions = new ArrayList<>();
        for (int i = 0; i < SHORT_NAMES.length; i++) {
            // ids deliberately unrelated to the expected order
            regions.add(region(100L + (i * 7L) % 20, SHORT_NAMES[i][0], SHORT_NAMES[i][1]));
        }

        List<RegionInfo> ordered = directory(regions, PostgresRegionDirectory.DEFAULT_ORDER).orderedRegions();

        assertThat(ordered).extracting(RegionInfo::nameRu)
                .containsExactly(java.util.Arrays.stream(SHORT_NAMES).map(names -> names[0]).toArray(String[]::new));
    }

    @Test
    void matchesDepartmentStyleNamesAndDeclensions() {
        List<Region> regions = List.of(
                region(1L, "Департамент экономических расследований по Алматинской области",
                        "Алматы облысы бойынша экономикалық тергеп-тексеру департаменті"),
                region(2L, "Департамент экономических расследований по городу Алматы",
                        "Алматы қаласы бойынша экономикалық тергеп-тексеру департаменті"),
                region(3L, "Департамент экономических расследований по городу Шымкенту",
                        "Шымкент қаласы бойынша экономикалық тергеп-тексеру департаменті"),
                region(4L, "Департамент экономических расследований по городу Астане",
                        "Астана қаласы бойынша экономикалық тергеп-тексеру департаменті"));

        List<RegionInfo> ordered = directory(regions, PostgresRegionDirectory.DEFAULT_ORDER).orderedRegions();

        assertThat(ordered).extracting(RegionInfo::id).containsExactly(4L, 2L, 3L, 1L);
    }

    @Test
    void almatyCityIsNotConfusedWithAlmatyOblastWhicheverComesFirst() {
        // The oblast sorts before the city here, so a naive "contains алматы" would pick it for row 2
        List<Region> regions = List.of(
                region(1L, "Алматинская область", "Алматы облысы"),
                region(2L, "город Алматы", "Алматы қаласы"));

        List<RegionInfo> ordered = directory(regions, PostgresRegionDirectory.DEFAULT_ORDER).orderedRegions();

        assertThat(ordered).extracting(RegionInfo::id).containsExactly(2L, 1L);
    }

    @Test
    void regionsMatchingNoEntryGoLastAlphabetically() {
        List<Region> regions = List.of(
                region(1L, "Центральный аппарат", "Орталық аппарат"),
                region(2L, "Акмолинская область", "Ақмола облысы"),
                region(3L, "Аппарат по особым делам", "Ерекше істер аппараты"),
                region(4L, "город Астана", "Астана қаласы"));

        List<RegionInfo> ordered = directory(regions, PostgresRegionDirectory.DEFAULT_ORDER).orderedRegions();

        assertThat(ordered).extracting(RegionInfo::id).containsExactly(4L, 2L, 3L, 1L);
    }

    @Test
    void orderCanBeOverriddenByProperty() {
        List<Region> regions = List.of(
                region(1L, "город Астана", "Астана қаласы"),
                region(2L, "город Шымкент", "Шымкент қаласы"));

        List<RegionInfo> ordered = directory(regions, "шымкент; астан").orderedRegions();

        assertThat(ordered).extracting(RegionInfo::id).containsExactly(2L, 1L);
    }

    @Test
    void exposesIdsAndBothNames() {
        List<RegionInfo> ordered = directory(List.of(region(5L, "город Астана", "Астана қаласы")),
                PostgresRegionDirectory.DEFAULT_ORDER).orderedRegions();

        assertThat(ordered).containsExactly(new RegionInfo(5L, "город Астана", "Астана қаласы"));
    }

    private static PostgresRegionDirectory directory(List<Region> regions, String order) {
        RegionRepository repository = mock(RegionRepository.class);
        // Same contract as the real query: sorted by ru_name
        List<Region> sorted = regions.stream().sorted(Comparator.comparing(Region::getRuName)).toList();
        when(repository.findAllByOrderByRuNameAsc()).thenReturn(sorted);
        return new PostgresRegionDirectory(repository, order);
    }

    private static Region region(Long id, String ruName, String kzName) {
        return Region.builder().id(id).ruName(ruName).kzName(kzName).build();
    }
}
