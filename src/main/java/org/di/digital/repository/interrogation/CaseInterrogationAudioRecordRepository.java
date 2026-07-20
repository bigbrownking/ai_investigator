package org.di.digital.repository.interrogation;

import org.di.digital.model.interrogation.CaseInterrogationAudioRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseInterrogationAudioRecordRepository extends JpaRepository<CaseInterrogationAudioRecord, Long> {
}
