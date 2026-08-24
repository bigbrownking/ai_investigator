package org.di.digital.repository.search;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.di.digital.dto.request.search.ReviewSearchRequest;
import org.di.digital.model.support.Review;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

public class ReviewSpecifications {

    public static Specification<Review> build(ReviewSearchRequest req) {
        return Specification
                .where(hasUser(req.getUser()))
                .and(hasSubject(req.getSubject()))
                .and(hasModule(req.getModule()))
                .and(createdBetween(req.getFrom(), req.getTo()));
    }

    private static Specification<Review> hasUser(String user) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(user)) return null;
            query.distinct(true);
            Join<Object, Object> u = root.join("user", JoinType.LEFT);
            String pattern = like(user);
            return cb.or(
                    cb.like(cb.lower(u.get("name")), pattern),
                    cb.like(cb.lower(u.get("surname")), pattern),
                    cb.like(cb.lower(u.get("fathername")), pattern),
                    cb.like(cb.lower(u.get("email")), pattern),
                    cb.like(cb.lower(u.get("iin")), pattern)
            );
        };
    }

    private static Specification<Review> hasSubject(String subject) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(subject)) return null;
            return cb.like(cb.lower(root.get("subject")), like(subject));
        };
    }

    private static Specification<Review> hasModule(String module) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(module)) return null;
            query.distinct(true);
            Join<Object, Object> items = root.join("items", JoinType.LEFT);
            return cb.like(cb.lower(items.get("module")), like(module));
        };
    }

    private static Specification<Review> createdBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return null;
            if (from != null && to != null) {
                return cb.between(root.get("createdAt"),
                        from.atStartOfDay(), to.plusDays(1).atStartOfDay());
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay());
            }
            return cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay());
        };
    }

    private static String like(String value) {
        return "%" + value.toLowerCase() + "%";
    }
}