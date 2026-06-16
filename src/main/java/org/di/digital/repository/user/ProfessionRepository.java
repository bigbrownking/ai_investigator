package org.di.digital.repository.user;

import org.di.digital.model.user.Profession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfessionRepository extends JpaRepository<Profession, Long> {
    @Query("""
    SELECT p FROM Profession p
    ORDER BY CASE p.id
        WHEN 2 THEN 1
        WHEN 1 THEN 2
        WHEN 6 THEN 3
        WHEN 5 THEN 4
        WHEN 3 THEN 5
        WHEN 4 THEN 6
        WHEN 7 THEN 7
        WHEN 8 THEN 8
        ELSE 9
    END
""")
    List<Profession> findAllOrdered();
}
