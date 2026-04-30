package org.di.digital.repository.search;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.di.digital.dto.request.search.AppealSearchRequest;
import org.di.digital.model.Appeal;
import org.di.digital.model.enums.AppealStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AppealSpecifications {
    public static Specification<Appeal> build(AppealSearchRequest req) {
        return Specification
                .where(hasStatus(req.getStatus()))
                .and(hasRegion(req.getRegion()))
                .and(hasFrom(req.getFrom()))
                .and(hasTo(req.getTo()))
                .and(createdOnDate(req.getCreatedAt()));
    }

    public static Specification<Appeal> buildForRegion(Long regionId, AppealSearchRequest req) {
        return Specification
                .where(inRegion(regionId))
                .and(hasStatus(req.getStatus()))
                .and(hasFrom(req.getFrom()))
                .and(hasTo(req.getTo()))
                .and(createdOnDate(req.getCreatedAt()));
    }

    private static Specification<Appeal> inRegion(Long regionId) {
        return (root, query, cb) ->
                cb.equal(root.get("region").get("id"), regionId);
    }

    private static Specification<Appeal> hasRegion(String region) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(region)) return null;
            query.distinct(true);
            Join<Object, Object> join = root.join("region", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(join.get("ruName")), like(region)),
                    cb.like(cb.lower(join.get("kzName")), like(region))
            );
        };
    }

    private static Specification<Appeal> hasStatus(String status) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(status)) return null;

            for (AppealStatus s : AppealStatus.values()) {
                if (s.name().equalsIgnoreCase(status) || s.getDescription().equalsIgnoreCase(status)) {
                    return cb.equal(root.get("status"), s);
                }
            }
            return null;
        };
    }

    private static Specification<Appeal> hasFrom(String from) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(from)) return null;
            query.distinct(true);
            Join<Object, Object> user = root.join("user", JoinType.LEFT);
            String pattern = like(from);
            return cb.or(
                    cb.like(cb.lower(user.get("name")), pattern),
                    cb.like(cb.lower(user.get("surname")), pattern),
                    cb.like(cb.lower(user.get("fathername")), pattern),
                    cb.like(cb.lower(user.get("email")), pattern),
                    cb.like(cb.lower(user.get("iin")), pattern)
            );
        };
    }

    private static Specification<Appeal> hasTo(String to) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(to)) return null;
            query.distinct(true);
            Join<Object, Object> reviewer = root.join("reviewedBy", JoinType.LEFT);
            String pattern = like(to);
            return cb.or(
                    cb.like(cb.lower(reviewer.get("name")), pattern),
                    cb.like(cb.lower(reviewer.get("surname")), pattern),
                    cb.like(cb.lower(reviewer.get("fathername")), pattern),
                    cb.like(cb.lower(reviewer.get("email")), pattern),
                    cb.like(cb.lower(reviewer.get("iin")), pattern)
            );
        };
    }

    private static Specification<Appeal> createdOnDate(LocalDate date) {
        return (root, query, cb) -> {
            if (date == null) return null;
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(23, 59, 59);
            return cb.between(root.get("createdAt"), start, end);
        };
    }

    private static String like(String value) {
        return "%" + value.toLowerCase() + "%";
    }
}
