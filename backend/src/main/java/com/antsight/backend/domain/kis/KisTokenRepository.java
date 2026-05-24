package com.antsight.backend.domain.kis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KisTokenRepository extends JpaRepository<KisToken, Long> {

    Optional<KisToken> findByEnvironment(String environment);
}
