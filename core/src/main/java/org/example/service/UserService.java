package org.example.service;

import org.example.User;
import org.example.dao.PasswordHistoryDAO;
import org.example.dao.UserDAO;
import org.example.dao.impl.PasswordHistoryDAOImpl;
import org.example.dao.impl.UserDAOImpl;
import org.example.security.PasswordHasher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    public User updateEmail(String nick, String newEmail) {
        User user = userDAO.findByNick(nick)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + nick));
        Optional<User> existingUser = userDAO.findByEmail(newEmail);
        if (existingUser.isPresent() && !existingUser.get().getNick().equals(nick)) {
            throw new IllegalArgumentException("Email уже используется другим пользователем: " + newEmail);
        }
        user.setEmail(newEmail);
        return userDAO.update(user);
    }

    public User updatePassword(String nick, String oldPassword, String newPassword) {
        User user = userDAO.findByNick(nick)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + nick));
        if (!PasswordHasher.verify(user.getPassword(), oldPassword)) {
            throw new IllegalArgumentException("Неверный текущий пароль");
        }
        validatePassword(nick, newPassword);
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

    public boolean updateNotificationFrequency(String nick, String frequency) {
        try {
            User user = userDAO.findByNick(nick)
                    .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + nick));
            user.setNotificationFrequency(frequency);
            userDAO.update(user);
            return true;
        } catch (Exception e) {
            System.err.println("Ошибка обновления частоты уведомлений: " + e.getMessage());
            return false;
        }
    }

    public User updatePreferences(String nick, String preferredRegion, String preferredLanguage) {
        User user = userDAO.findByNick(nick)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + nick));
        user.setPreferredRegion(preferredRegion);
        user.setPreferredLanguage(preferredLanguage);
        return userDAO.update(user);
    }

    public User incrementReadArticles(String nick) {
        User user = userDAO.findByNick(nick)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + nick));
        user.incrementReadArticlesCount();
        return userDAO.update(user);
    }

    public User setAdminStatus(String nick, boolean isAdmin) {
        User user = userDAO.findByNick(nick)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + nick));
        user.setAdmin(isAdmin);
        return userDAO.update(user);
    }

    // ═══════════════════════════════════════════════════════════
    // Поиск
    // ═══════════════════════════════════════════════════════════

    public Optional<User> getUserByNick(String nick) {
        return userDAO.findByNick(nick);
    }

    public Optional<User> getUserByEmail(String email) {
        return userDAO.findByEmail(email);
    }

    public List<User> getAllUsers() {
        return userDAO.findAll();
    }

    public List<User> getAllAdmins() {
        return userDAO.findAllAdmins();
    }

    public List<User> getUsersByRegion(String region) {
        return userDAO.findByPreferredRegion(region);
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

    public void deleteUser(String nick) {
        userDAO.findByNick(nick).ifPresent(u -> userDAO.deleteById(u.getId()));
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
