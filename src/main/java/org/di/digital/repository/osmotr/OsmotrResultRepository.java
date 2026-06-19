package org.di.digital.repository.osmotr;

import org.di.digital.model.osmotr.OsmotrResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OsmotrResultRepository extends JpaRepository<OsmotrResult, Long> {
    List<OsmotrResult> findByCaseNumber(String caseNumber);
}