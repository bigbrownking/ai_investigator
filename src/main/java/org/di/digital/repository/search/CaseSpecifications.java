package org.di.digital.repository.search;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.di.digital.dto.request.search.CaseSearchRequest;
import org.di.digital.model.Case;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

public class CaseSpecifications {

    public static Specification<Case> build(CaseSearchRequest req) {
        return Specification
                .where(hasNumber(req.getNumber()))
                .and(hasTitle(req.getTitle()))
                .and(isActive(req.getStatus()))
                .and(createdAfter(req.getCreatedFrom()))
                .and(createdBefore(req.getCreatedTo()))
                .and(hasOwnerName(req.getOwnerName()))
                .and(hasRegion(req.getRegion()));
    }

    public static Specification<Case> buildForRegion(Long regionId, CaseSearchRequest req) {
        return Specification
                .where(inRegion(regionId))
                .and(hasNumber(req.getNumber()))
                .and(hasTitle(req.getTitle()))
                .and(isActive(req.getStatus()))
                .and(createdAfter(req.getCreatedFrom()))
                .and(createdBefore(req.getCreatedTo()))
                .and(hasOwnerName(req.getOwnerName()));
    }

    private static Specification<Case> inRegion(Long regionId) {
        return (root, query, cb) ->
                cb.equal(root.get("owner").get("region").get("id"), regionId);
    }

    private static Specification<Case> hasNumber(String number) {
        return (root, query, cb) ->
                StringUtils.hasText(number)
                        ? cb.like(cb.lower(root.get("number")), like(number))
                        : null;
    }

    private static Specification<Case> hasTitle(String title) {
        return (root, query, cb) ->
                StringUtils.hasText(title)
                        ? cb.like(cb.lower(root.get("title")), like(title))
                        : null;
    }

    private static Specification<Case> isActive(Boolean status) {
        return (root, query, cb) ->
                status != null
                        ? cb.equal(root.get("status"), status)
                        : null;
    }

    private static Specification<Case> createdAfter(LocalDate from) {
        return (root, query, cb) ->
                from != null
                        ? cb.greaterThanOrEqualTo(root.get("createdDate"), from.atStartOfDay())
                        : null;
    }

    private static Specification<Case> createdBefore(LocalDate to) {
        return (root, query, cb) ->
                to != null
                        ? cb.lessThanOrEqualTo(root.get("createdDate"), to.plusDays(1).atStartOfDay())
                        : null;
    }

    private static Specification<Case> hasOwnerName(String ownerName) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(ownerName)) return null;
            query.distinct(true);
            Join<Object, Object> owner = root.join("owner", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(owner.get("name")), like(ownerName)),
                    cb.like(cb.lower(owner.get("surname")), like(ownerName)),
                    cb.like(cb.lower(owner.get("fathername")), like(ownerName))
            );
        };
    }

    private static Specification<Case> hasRegion(String region) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(region)) return null;
            query.distinct(true);
            Join<Object, Object> owner = root.join("owner", JoinType.LEFT);
            Join<Object, Object> regionJoin = owner.join("region", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(regionJoin.get("ruName")), like(region)),
                    cb.like(cb.lower(regionJoin.get("kzName")), like(region))
            );
        };
    }
    public static Specification<Case> hasOwner(Long userId) {
        return (root, query, cb) ->
                cb.equal(root.get("owner").get("id"), userId);
    }

    private static String like(String value) {
        return "%" + value.toLowerCase() + "%";
    }
}