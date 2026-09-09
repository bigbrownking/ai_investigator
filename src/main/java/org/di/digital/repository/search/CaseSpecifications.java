package org.di.digital.repository.search;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.di.digital.dto.request.search.CaseSearchRequest;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.RejectionReasonStatus;
import org.di.digital.model.enums.cases.CaseRejectionReason;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class CaseSpecifications {

    public static Specification<Case> build(CaseSearchRequest req) {
        return Specification
                .where(hasNumber(req.getNumber()))
                .and(hasTitle(req.getTitle()))
                .and(isActive(req.getStatus()))
                .and(createdAfter(req.getFrom()))
                .and(createdBefore(req.getTo()))
                .and(hasOwnerName(req.getOwnerName()))
                .and(hasRegion(req.getRegion()))
                .and(hasRejectionReason(req.getRejectionReason()));
    }
    public static Specification<Case> buildForRegions(List<Long> regionIds, CaseSearchRequest req) {
        return Specification
                .where(inRegions(regionIds))
                .and(hasNumber(req.getNumber()))
                .and(hasTitle(req.getTitle()))
                .and(isActive(req.getStatus()))
                .and(createdAfter(req.getFrom()))
                .and(createdBefore(req.getTo()))
                .and(hasOwnerName(req.getOwnerName()));
    }

    private static Specification<Case> inRegions(List<Long> regionIds) {
        return (root, query, cb) ->
                root.get("owner").get("region").get("id").in(regionIds);
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
            String pattern = "%" + ownerName.toLowerCase().trim() + "%";
            return cb.or(
                    cb.like(cb.lower(owner.get("name")), pattern),
                    cb.like(cb.lower(owner.get("surname")), pattern),
                    cb.like(cb.lower(owner.get("fathername")), pattern)
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
                    cb.like(cb.lower(regionJoin.get("ruName")), region.toLowerCase()),
                    cb.like(cb.lower(regionJoin.get("kzName")), region.toLowerCase())
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
    public static Specification<Case> forUser(String email) {
        return (root, query, cb) -> {
            query.distinct(true);
            var ownerMatch = cb.equal(root.get("owner").get("email"), email);
            var memberSub = query.subquery(Long.class);
            var subRoot = memberSub.from(Case.class);
            var users = subRoot.join("users", JoinType.INNER);
            memberSub.select(subRoot.get("id"))
                    .where(
                            cb.equal(subRoot.get("id"), root.get("id")),
                            cb.equal(users.get("email"), email)
                    );
            return cb.or(ownerMatch, cb.exists(memberSub));
        };
    }
    private static Specification<Case> hasRejectionReason(CaseRejectionReason reason) {
        return (root, query, cb) -> {
            if (reason == null) return null;

            var maxSub = query.subquery(LocalDateTime.class);
            var maxRrs = maxSub.from(RejectionReasonStatus.class);
            maxSub.select(cb.<LocalDateTime>greatest(maxRrs.get("timestamp")))
                    .where(cb.equal(maxRrs.get("caseId"), root.get("id")));

            var sub = query.subquery(Long.class);
            var rrs = sub.from(RejectionReasonStatus.class);
            sub.select(rrs.get("id"))
                    .where(
                            cb.equal(rrs.get("caseId"), root.get("id")),
                            cb.equal(rrs.get("rejectionReason"), reason),
                            cb.equal(rrs.<LocalDateTime>get("timestamp"), maxSub)
                    );
            return cb.exists(sub);
        };
    }
}