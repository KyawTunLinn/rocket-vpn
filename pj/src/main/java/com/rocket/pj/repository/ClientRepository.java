package com.rocket.pj.repository;

import com.rocket.pj.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByPublicKey(String publicKey);

    boolean existsByAddress(String address);

    boolean existsByName(String name);
}
