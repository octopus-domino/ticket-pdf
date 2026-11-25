package ru.ticket.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.ticket.auth.domain.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}