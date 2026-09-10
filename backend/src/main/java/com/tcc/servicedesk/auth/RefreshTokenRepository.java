package com.tcc.servicedesk.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tcc.servicedesk.user.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken , Long>{

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserAndRevokedFalse(User user);
}
