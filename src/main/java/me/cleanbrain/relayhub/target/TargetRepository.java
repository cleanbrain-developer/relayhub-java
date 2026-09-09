package me.cleanbrain.relayhub.target;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TargetRepository extends JpaRepository<Target, UUID> {

    Optional<Target> findByKey(String key);
}
