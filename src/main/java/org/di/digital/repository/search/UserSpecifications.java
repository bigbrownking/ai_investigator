package org.di.digital.repository.search;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.di.digital.dto.request.search.UserSearchRequest;
import org.di.digital.model.user.User;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

public class UserSpecifications {
    public static Specification<User> build(UserSearchRequest req) {
        return Specification
                .where(hasIin(req.getIin()))
                .and(hasFio(req.getFio()))
                .and(hasEmail(req.getEmail()))
                .and(hasProfession(req.getProfession()))
                .and(hasAdministration(req.getAdministration()))
                .and(hasRegion(req.getRegion()))
                .and(createdBetween(req.getFrom(), req.getTo()))
                .and(isActive(req.getActive()));
    }

    public static Specification<User> buildForRegions(List<Long> regionIds, UserSearchRequest req) {
        return Specification
                .where(inRegions(regionIds))
                .and(hasIin(req.getIin()))
                .and(hasFio(req.getFio()))
                .and(hasEmail(req.getEmail()))
                .and(hasProfession(req.getProfession()))
                .and(hasAdministration(req.getAdministration()))
                .and(isActive(req.getActive()));
    }

    public static Specification<User> buildForRegion(Long regionId, UserSearchRequest req) {
        return Specification
                .where(inRegion(regionId))
                .and(hasIin(req.getIin()))
                .and(hasFio(req.getFio()))
                .and(hasEmail(req.getEmail()))
                .and(hasProfession(req.getProfession()))
                .and(hasAdministration(req.getAdministration()))
                .and(isActive(req.getActive()));
    }

    private static Specification<User> inRegions(List<Long> regionIds) {
        return (root, query, cb) ->
                root.get("region").get("id").in(regionIds);
    }

    private static Specification<User> inRegion(Long regionId) {
        return (root, query, cb) ->
                cb.equal(root.get("region").get("id"), regionId);
    }

    private static Specification<User> hasIin(String iin) {
        return (root, query, cb) ->
                StringUtils.hasText(iin)
                        ? cb.like(cb.lower(root.get("iin")), like(iin))
                        : null;
    }

    private static Specification<User> hasName(String name) {
        return (root, query, cb) ->
                StringUtils.hasText(name)
                        ? cb.like(cb.lower(root.get("name")), like(name))
                        : null;
    }

    private static Specification<User> hasSurname(String surname) {
        return (root, query, cb) ->
                StringUtils.hasText(surname)
                        ? cb.like(cb.lower(root.get("surname")), like(surname))
                        : null;
    }

    private static Specification<User> hasFathername(String fathername) {
        return (root, query, cb) ->
                StringUtils.hasText(fathername)
                        ? cb.like(cb.lower(root.get("fathername")), like(fathername))
                        : null;
    }

    private static Specification<User> hasEmail(String email) {
        return (root, query, cb) ->
                StringUtils.hasText(email)
                        ? cb.like(cb.lower(root.get("email")), like(email))
                        : null;
    }

    // TODO use id instead of name
    private static Specification<User> hasProfession(String profession) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(profession)) return null;
            query.distinct(true);
            Join<Object, Object> join = root.join("profession", JoinType.LEFT);
            return cb.or(
                    cb.equal(cb.lower(join.get("ruName")), profession.toLowerCase()),
                    cb.equal(cb.lower(join.get("kzName")), profession.toLowerCase())
            );
        };
    }

    private static Specification<User> hasAdministration(String administration) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(administration)) return null;
            query.distinct(true);
            Join<Object, Object> join = root.join("administration", JoinType.LEFT);
            return cb.or(
                    cb.equal(cb.lower(join.get("ruName")), administration.toLowerCase()),
                    cb.equal(cb.lower(join.get("kzName")), administration.toLowerCase())
            );
        };
    }

    private static Specification<User> hasRegion(String region) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(region)) return null;
            query.distinct(true);
            Join<Object, Object> join = root.join("region", JoinType.LEFT);
            return cb.or(
                    cb.equal(cb.lower(join.get("ruName")), region.toLowerCase()),
                    cb.equal(cb.lower(join.get("kzName")), region.toLowerCase())
            );
        };
    }

    private static Specification<User> isActive(Boolean active) {
        return (root, query, cb) ->
                active != null
                        ? cb.equal(root.get("active"), active)
                        : null;
    }
    private static Specification<User> createdBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return null;
            if (from != null && to != null) {
                return cb.between(root.get("createdDate"),
                        from.atStartOfDay(), to.plusDays(1).atStartOfDay());
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("createdDate"), from.atStartOfDay());
            }
            return cb.lessThan(root.get("createdDate"), to.plusDays(1).atStartOfDay());
        };
    }
    private static Specification<User> hasFio(String fio) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(fio)) return null;

            String[] tokens = fio.trim().toLowerCase().split("\\s+");

            Predicate result = cb.conjunction();
            for (String token : tokens) {
                String pattern = "%" + token + "%";
                result = cb.and(result, cb.or(
                        cb.like(cb.lower(root.get("surname")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("fathername")), pattern)
                ));
            }
            return result;
        };
    }

    private static String like(String value) {
        return "%" + value.toLowerCase() + "%";
    }
}