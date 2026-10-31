package org.example.service;

import org.example.Region;
import org.example.Role;
import org.example.User;
import org.example.dao.PasswordHistoryDAO;
import org.example.dao.UserDAO;
import org.example.dao.impl.PasswordHistoryDAOImpl;
import org.example.dao.impl.UserDAOImpl;
import org.example.security.PasswordHasher;
import org.example.util.HibernateUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class UserService {

    private final UserDAO userDAO;
    private final PasswordHistoryDAO passwordHistoryDAO;

    // Фиктивный хеш для constant-time ответа при несуществующем нике/email.
    // Вычисляется один раз при загрузке класса; пароль-сентинель не логируется.
    private static final String DUMMY_HASH =
            PasswordHasher.hash("__newsdove_timing_sentinel__");

    public UserService() {
        this.userDAO             = new UserDAOImpl();
        this.passwordHistoryDAO  = new PasswordHistoryDAOImpl();
    }

    public UserService(UserDAO userDAO) {
        this.userDAO             = userDAO;
        this.passwordHistoryDAO  = new PasswordHistoryDAOImpl();
    }

    public UserService(UserDAO userDAO, PasswordHistoryDAO passwordHistoryDAO) {
        this.userDAO             = userDAO;
        this.passwordHistoryDAO  = passwordHistoryDAO;
    }

    // ═══════════════════════════════════════════════════════════
    // Регистрация и аутентификация
    // ═══════════════════════════════════════════════════════════

    /** Регистрация без email (для UI-формы регистрации). */
    public User registerUserNoEmail(String nick, String password) {
        validatePassword(nick, password);
        if (userDAO.findByNick(nick).isPresent()) {
            throw new IllegalArgumentException("Login is already taken");
        }
        String hash = PasswordHasher.hash(password);
        return userDAO.saveWithHistory(new User(nick, hash, null));
    }

    public User registerUser(String nick, String password, String email) {
        validatePassword(nick, password);
        if (userDAO.findByNick(nick).isPresent()) {
            throw new IllegalArgumentException("Пользователь с таким ником уже существует: " + nick);
        }
        if (userDAO.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует: " + email);
        }
        String hash = PasswordHasher.hash(password);
        return userDAO.saveWithHistory(new User(nick, hash, email));
    }

    /** Аутентификация по нику. Constant-time: verify всегда вызывается. */
    public Optional<User> authenticate(String nick, String password) {
        Optional<User> userOpt = userDAO.findByNick(nick);
        String hashToVerify = userOpt.map(User::getPassword).orElse(DUMMY_HASH);
        boolean match = PasswordHasher.verify(hashToVerify, password);
        return (userOpt.isPresent() && match) ? userOpt : Optional.empty();
    }

    /** Аутентификация по email. Constant-time: verify всегда вызывается. */
    public Optional<User> authenticateByEmail(String email, String password) {
        Optional<User> userOpt = userDAO.findByEmail(email);
        String hashToVerify = userOpt.map(User::getPassword).orElse(DUMMY_HASH);
        boolean match = PasswordHasher.verify(hashToVerify, password);
        return (userOpt.isPresent() && match) ? userOpt : Optional.empty();
    }

    // ═══════════════════════════════════════════════════════════
    // Управление профилем
    // ═══════════════════════════════════════════════════════════

    /**
     * Универсальное обновление пользователя — сохраняет объект целиком.
     * Используется из контроллеров когда нужно сохранить все поля сразу.
     */
    public boolean updateUser(User user) {
        try {
            userDAO.update(user);
            return true;
        } catch (Exception e) {
            System.err.println("Ошибка обновления пользователя: " + e.getMessage());
            return false;
        }
    }

    public User updateEmail(int userId, String newEmail) {
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        Optional<User> existingUser = userDAO.findByEmail(newEmail);
        if (existingUser.isPresent() && existingUser.get().getId() != userId) {
            throw new IllegalArgumentException("Email уже используется другим пользователем: " + newEmail);
        }
        user.setEmail(newEmail);
        return userDAO.update(user);
    }

    public User updatePassword(int userId, String oldPassword, String newPassword) {
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (!PasswordHasher.verify(user.getPassword(), oldPassword)) {
            throw new IllegalArgumentException("Неверный текущий пароль");
        }
        validatePassword(user.getNick(), newPassword);
        List<String> last3 = passwordHistoryDAO.findLastHashes(user.getId(), 3);
        for (String oldHash : last3) {
            if (PasswordHasher.verify(oldHash, newPassword)) {
                throw new IllegalArgumentException("Нельзя повторять один из последних 3 паролей");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        String newHash = PasswordHasher.hash(newPassword);
        userDAO.changePassword(user.getId(), newHash, now);
        user.setPassword(newHash);
        user.setPasswordChangedAt(now);
        return user;
    }

    public boolean updateNotificationFrequency(int userId, String frequency) {
        try {
            User user = userDAO.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            user.setNotificationFrequency(frequency);
            userDAO.update(user);
            return true;
        } catch (Exception e) {
            System.err.println("Ошибка обновления частоты уведомлений: " + e.getMessage());
            return false;
        }
    }

    public User setLanguages(int userId, Set<String> languages) {
        for (String lang : languages) {
            if (!lang.matches("^[a-z]{2}$")) {
                throw new IllegalArgumentException("Invalid language code (must be 2 lowercase letters): " + lang);
            }
        }
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setLanguages(languages);
        return userDAO.update(user);
    }

    public User setRegions(int userId, Set<String> regionKeys) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            for (String key : regionKeys) {
                if (session.find(Region.class, key) == null) {
                    throw new IllegalArgumentException("Unknown region_key: " + key);
                }
            }
        }
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setRegions(regionKeys);
        return userDAO.update(user);
    }

    public User incrementReadArticles(int userId) {
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.incrementReadArticlesCount();
        return userDAO.update(user);
    }

    public User setRole(int userId, Role role) {
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setRole(role);
        return userDAO.update(user);
    }

    // ═══════════════════════════════════════════════════════════
    // Поиск
    // ═══════════════════════════════════════════════════════════

    public Optional<User> getUserById(int userId) {
        return userDAO.findById(userId);
    }

    public Optional<User> getUserByNick(String nick) {
        return userDAO.findByNick(nick);
    }

    public Optional<User> getUserByEmail(String email) {
        return userDAO.findByEmail(email);
    }

    public List<User> getAllUsers() {
        return userDAO.findAll();
    }

    public List<User> getUsersByRole(Role role) {
        return userDAO.findByRole(role);
    }

    public List<User> getUsersByRegion(String regionKey) {
        return userDAO.findByRegion(regionKey);
    }

    public List<User> getActiveReaders(int minReadCount) {
        return userDAO.findByReadArticlesGreaterThan(minReadCount);
    }

    public long getTotalUsers() {
        return userDAO.count();
    }

    // ═══════════════════════════════════════════════════════════
    // Удаление
    // ═══════════════════════════════════════════════════════════

    public void deleteUser(int userId) {
        userDAO.findById(userId).ifPresent(u -> userDAO.deleteById(u.getId()));
    }

    // ═══════════════════════════════════════════════════════════
    // Валидация пароля
    // ═══════════════════════════════════════════════════════════

    private static void validatePassword(String nick, String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Пароль должен содержать не менее 8 символов");
        }
        if (password.length() > PasswordHasher.MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Пароль не должен превышать " + PasswordHasher.MAX_PASSWORD_LENGTH + " символов");
        }
        if (password.equalsIgnoreCase(nick)) {
            throw new IllegalArgumentException("Пароль не должен совпадать с ником");
        }
    }
}
