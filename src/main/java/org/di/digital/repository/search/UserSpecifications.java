package org.di.digital.repository.search;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.di.digital.dto.request.search.UserSearchRequest;
import org.di.digital.model.User;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class UserSpecifications {

    public static Specification<User> build(UserSearchRequest req) {
        return Specification
                .where(hasIin(req.getIin()))
                .and(hasName(req.getName()))
                .and(hasSurname(req.getSurname()))
                .and(hasFathername(req.getFathername()))
                .and(hasEmail(req.getEmail()))
                .and(hasProfession(req.getProfession()))
                .and(hasAdministration(req.getAdministration()))
                .and(hasRegion(req.getRegion()))
                .and(isActive(req.getActive()));
    }

    public static Specification<User> buildForRegion(Long regionId, UserSearchRequest req) {
        return Specification
                .where(inRegion(regionId))
                .and(hasIin(req.getIin()))
                .and(hasName(req.getName()))
                .and(hasSurname(req.getSurname()))
                .and(hasFathername(req.getFathername()))
                .and(hasEmail(req.getEmail()))
                .and(hasProfession(req.getProfession()))
                .and(hasAdministration(req.getAdministration()))
                .and(isActive(req.getActive()));
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

    private static Specification<User> hasProfession(String profession) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(profession)) return null;
            query.distinct(true);
            Join<Object, Object> join = root.join("profession", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(join.get("ruName")), like(profession)),
                    cb.like(cb.lower(join.get("kzName")), like(profession))
            );
        };
    }

    private static Specification<User> hasAdministration(String administration) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(administration)) return null;
            query.distinct(true);
            Join<Object, Object> join = root.join("administration", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(join.get("ruName")), like(administration)),
                    cb.like(cb.lower(join.get("kzName")), like(administration))
            );
        };
    }

    private static Specification<User> hasRegion(String region) {
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

    private static Specification<User> isActive(Boolean active) {
        return (root, query, cb) ->
                active != null
                        ? cb.equal(root.get("active"), active)
                        : null;
    }

    private static String like(String value) {
        return "%" + value.toLowerCase() + "%";
    }
}