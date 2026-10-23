package org.di.digital.repository.search;

import org.di.digital.model.zonal.ZonalTemplate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class ZonalSpecifications {
    public static Specification<ZonalTemplate> build(String name, Boolean active, Boolean isDeleted) {
        return Specification
                .where(isDeleted(isDeleted))
                .and(isActive(active))
                .and(hasName(name));
    }

    private static Specification<ZonalTemplate> hasName(String name) {
        return (root, query, cb) ->
                StringUtils.hasText(name)
                        ? cb.like(cb.lower(root.get("name")), like(name.trim()))
                        : null;
    }

    private static Specification<ZonalTemplate> isActive(Boolean active) {
        return (root, query, cb) ->
                active != null ? cb.equal(root.get("active"), active) : null;
    }

    private static Specification<ZonalTemplate> isDeleted(Boolean isDeleted) {
        return (root, query, cb) ->
                isDeleted != null
                        ? cb.equal(root.get("isDeleted"), isDeleted)
                        : null;
    }

    private static String like(String value) {
        return "%" + value.toLowerCase() + "%";
    }
}
