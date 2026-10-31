package org.example.dao;

import org.example.Role;
import org.example.User;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface UserDAO extends GenericDAO<User, Integer> {

    Optional<User> findByNick(String nick);

    /**
     * Находит пользователя по email.
     *
     * @param email адрес электронной почты
     * @return Optional с найденным пользователем или пустой Optional
     */
    Optional<User> findByEmail(String email);

    List<User> findByRole(Role role);

    List<User> findByRegion(String regionKey);

    /**
     * Находит пользователей, прочитавших больше указанного количества статей.
     *
     * @param minCount минимальное количество прочитанных статей
     * @return список пользователей
     */
    List<User> findByReadArticlesGreaterThan(int minCount);

    /** Persist User + initial PasswordHistory in one transaction. */
    User saveWithHistory(User user);

    /** UPDATE user(password, password_changed_at) + INSERT password_history in one transaction. */
    void changePassword(int userId, String newHash, LocalDateTime now);
}