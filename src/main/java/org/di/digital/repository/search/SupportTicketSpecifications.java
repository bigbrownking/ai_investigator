package org.di.digital.repository.search;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.di.digital.dto.request.search.SupportTicketSearchRequest;
import org.di.digital.model.support.SupportTicket;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

public class SupportTicketSpecifications {

    public static Specification<SupportTicket> build(SupportTicketSearchRequest req) {
        return Specification
                .where(hasUser(req.getUser()))
                .and(hasMessage(req.getMessage()))
                .and(hasPhoneNumber(req.getPhoneNumber()))
                .and(createdBetween(req.getFrom(), req.getTo()));
    }

    private static Specification<SupportTicket> hasUser(String user) {
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

    private static Specification<SupportTicket> hasMessage(String message) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(message)) return null;
            return cb.like(cb.lower(root.get("message")), like(message));
        };
    }

    private static Specification<SupportTicket> hasPhoneNumber(String phone) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(phone)) return null;
            return cb.like(root.get("phoneNumber"), "%" + phone + "%");
        };
    }

    private static Specification<SupportTicket> createdBetween(LocalDate from, LocalDate to) {
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