package org.di.digital.repository;

import org.di.digital.model.CaseInterrogationOtherAudio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseInterrogationIOtherAudioRepository extends JpaRepository<CaseInterrogationOtherAudio, Long> {
}
