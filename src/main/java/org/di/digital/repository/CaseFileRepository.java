package org.di.digital.repository;

import org.di.digital.model.CaseFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CaseFileRepository extends JpaRepository<CaseFile, Long> {
}
