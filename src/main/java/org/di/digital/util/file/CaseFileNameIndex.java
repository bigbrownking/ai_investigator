package org.di.digital.util.file;

import org.di.digital.model.cases.CaseFile;

import java.text.Normalizer;
import java.util.*;


public final class CaseFileNameIndex {

    private final Map<String, CaseFile> exact = new HashMap<>();
    private final Map<String, CaseFile> normalized = new HashMap<>();
    private final Map<String, List<CaseFile>> withoutExt = new HashMap<>();

    private CaseFileNameIndex(Collection<CaseFile> files) {
        if (files == null) return;
        for (CaseFile f : files) {
            put(exact, f.getOriginalFileName(), f);
            put(exact, f.getStoredFileName(), f);
            put(normalized, normalize(f.getOriginalFileName()), f);
            put(normalized, normalize(f.getStoredFileName()), f);
            String base = stripExtension(normalize(f.getOriginalFileName()));
            if (!base.isEmpty()) {
                withoutExt.computeIfAbsent(base, k -> new ArrayList<>()).add(f);
            }
        }
    }

    public static CaseFileNameIndex of(Collection<CaseFile> files) {
        return new CaseFileNameIndex(files);
    }

    public Optional<CaseFile> find(String rawName) {
        if (rawName == null || rawName.isBlank()) return Optional.empty();

        CaseFile f = exact.get(rawName);
        if (f != null) return Optional.of(f);

        String n = normalize(rawName);
        f = normalized.get(n);
        if (f != null) return Optional.of(f);

        List<CaseFile> byBase = withoutExt.getOrDefault(stripExtension(n), List.of());
        return byBase.size() == 1 ? Optional.of(byBase.get(0)) : Optional.empty();
    }

    private static void put(Map<String, CaseFile> map, String key, CaseFile f) {
        if (key != null && !key.isEmpty()) map.putIfAbsent(key, f);
    }

    public static String normalize(String name) {
        if (name == null) return "";
        return Normalizer.normalize(name, Normalizer.Form.NFC)
                .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}