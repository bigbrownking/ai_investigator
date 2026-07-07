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
       WHERE p.id <= 7
       ORDER BY CASE p.id
           WHEN 2 THEN 1
           WHEN 1 THEN 2
           WHEN 6 THEN 3
           WHEN 5 THEN 4
           WHEN 3 THEN 5
           WHEN 4 THEN 6
           WHEN 7 THEN 7
           ELSE 9
       END
""")
    List<Profession> findAllOrdered();

    @Query("""
    SELECT p FROM Profession p
          WHERE p.id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
          ORDER BY CASE p.id
              WHEN 2 THEN 1
              WHEN 1 THEN 2
              WHEN 6 THEN 3
              WHEN 5 THEN 4
              WHEN 3 THEN 5
              WHEN 4 THEN 6
              WHEN 7 THEN 7
              WHEN 12 THEN 8
              WHEN 10 THEN 9
              WHEN 11 THEN 10
              WHEN 9 THEN 11
              WHEN 8 THEN 12
              ELSE 13
          END
""")
    List<Profession> findAllForAdmin();
}
