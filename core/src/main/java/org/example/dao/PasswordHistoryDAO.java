package org.example.dao;

import org.example.PasswordHistory;
import java.util.List;

public interface PasswordHistoryDAO extends GenericDAO<PasswordHistory, Integer> {

    /**
     * Returns the last n password hashes for the given user, newest first.
     * Used to enforce password reuse policy.
     */
    List<String> findLastHashes(int userId, int n);
}
