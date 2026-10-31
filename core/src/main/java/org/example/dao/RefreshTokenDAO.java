package org.example.dao;

import org.example.RefreshToken;

import java.util.Optional;

public interface RefreshTokenDAO {
    RefreshToken save(RefreshToken token);
    Optional<RefreshToken> findByHash(String tokenHash);
    void revokeById(long id);
}
