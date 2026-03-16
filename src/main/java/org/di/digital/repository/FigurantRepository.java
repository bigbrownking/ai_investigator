package org.di.digital.repository;

import org.di.digital.model.Figurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FigurantRepository extends JpaRepository<Figurant, Long> {
}
