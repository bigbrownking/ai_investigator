package org.di.digital.repository.osmotr;

import org.di.digital.model.osmotr.OsmotrResultSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OsmotrResultSegmentRepository extends JpaRepository<OsmotrResultSegment, Long> {
}