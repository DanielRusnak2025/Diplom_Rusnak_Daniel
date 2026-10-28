package org.example.service;

import org.example.User;
import org.example.dao.UserDAO;
import org.example.dao.impl.UserDAOImpl;

import java.util.List;
import java.util.Optional;

public class UserService {

    private final UserDAO userDAO;

    public UserService() {
        this.userDAO = new UserDAOImpl();
    }

    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    // ═══════════════════════════════════════════════════════════
    // Регистрация и аутентификация
    // ═══════════════════════════════════════════════════════════

    /** Регистрация без email (для UI-формы регистрации). */
    public User registerUserNoEmail(String nick, String password) {
        if (userDAO.findByNick(nick).isPresent()) {
            throw new IllegalArgumentException("Login is already taken");
        }
        User user = new User(nick, password, null);
        return userDAO.save(user);
    }

    public User registerUser(String nick, String password, String email) {
        if (userDAO.findByNick(nick).isPresent()) {
            throw new IllegalArgumentException("Пользователь с таким ником уже существует: " + nick);
        }
        Optional<User> existingUser = userDAO.findByEmail(email);
        if (existingUser.isPresent()) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует: " + email);
        }
        User user = new User(nick, password, email);
        return userDAO.save(user);
    }

    public Optional<User> authenticate(String nick, String password) {
        Optional<User> userOpt = userDAO.findByNick(nick);
        if (userOpt.isPresent() && userOpt.get().getPassword().equals(password)) {
            return userOpt;
        }
        return Optional.empty();
    }

    public Optional<User> authenticateByEmail(String email, String password) {
        Optional<User> userOpt = userDAO.findByEmail(email);
        if (userOpt.isPresent() && userOpt.get().getPassword().equals(password)) {
            return userOpt;
        }
        return Optional.empty();
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
        if (!user.getPassword().equals(oldPassword)) {
            throw new IllegalArgumentException("Неверный текущий пароль");
        }
        user.setPassword(newPassword);
        return userDAO.update(user);
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
}