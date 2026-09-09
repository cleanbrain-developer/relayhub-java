package me.cleanbrain.relayhub.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SourceRepository extends JpaRepository<Source, UUID> {

    Optional<Source> findByKey(String key);
}
