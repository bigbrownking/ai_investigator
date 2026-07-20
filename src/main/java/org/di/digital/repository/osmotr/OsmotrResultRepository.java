package org.di.digital.repository.osmotr;

import org.di.digital.model.osmotr.OsmotrResult;
import org.di.digital.model.osmotr.OsmotrResultSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OsmotrResultRepository extends JpaRepository<OsmotrResult, Long> {
    List<OsmotrResult> findByCaseNumber(String caseNumber);
    Optional<OsmotrResult> findFirstByCaseNumber(String caseNumber);
    @Query("""
        SELECT DISTINCT r
        FROM OsmotrResult r
        JOIN r.segments s
        WHERE r.caseNumber = :caseNumber
        AND LOWER(s.inspectionText) LIKE LOWER(CONCAT('%', :query, '%'))
        ORDER BY r.createdAt DESC
        """)
    List<OsmotrResult> searchBySegmentText(@Param("caseNumber") String caseNumber,
                                           @Param("query") String query);
}