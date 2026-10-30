package org.example;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(length = 50, unique = true, nullable = false)
    private String nick;

    @Column(length = 255, nullable = false)
    private String password;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "registration_date")
    @Temporal(TemporalType.DATE)
    private Date registrationDate;

    @Column(length = 100, unique = true)
    private String email;

    @Column(name = "preferred_region", length = 100)
    private String preferredRegion;

    @Column(name = "preferred_language", length = 50)
    private String preferredLanguage;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", length = 20, nullable = false)
    private Role role = Role.USER;

    @Column(name = "read_articles_count")
    private int readArticlesCount;

    @Column(name = "notification_frequency", length = 10, nullable = false)
    private String notificationFrequency = "never";

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserTagPercent> userTagPercents = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Report> reports = new HashSet<>();

    // ───────────────────────────────────────────────
    // Конструкторы
    // ───────────────────────────────────────────────

    public User() {
    }

    public User(String nick, String password, String email) {
        this.nick = nick;
        this.password = password;
        this.email = email;
        this.registrationDate = new Date();
        this.readArticlesCount = 0;
        this.role = Role.USER;
    }

    // ───────────────────────────────────────────────
    // Геттеры и сеттеры
    // ───────────────────────────────────────────────

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(LocalDateTime passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public void setFailedLoginCount(int failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Date getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(Date registrationDate) {
        this.registrationDate = registrationDate;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPreferredRegion() {
        return preferredRegion;
    }

    public void setPreferredRegion(String preferredRegion) {
        this.preferredRegion = preferredRegion;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public int getReadArticlesCount() {
        return readArticlesCount;
    }

    public void setReadArticlesCount(int readArticlesCount) {
        this.readArticlesCount = readArticlesCount;
    }

    public void incrementReadArticlesCount() {
        this.readArticlesCount++;
    }

    public String getNotificationFrequency() {
        return notificationFrequency != null ? notificationFrequency : "never";
    }

    public void setNotificationFrequency(String notificationFrequency) {
        this.notificationFrequency = notificationFrequency;
    }

    public Set<UserTagPercent> getUserTagPercents() {
        return userTagPercents;
    }

    public void setUserTagPercents(Set<UserTagPercent> userTagPercents) {
        this.userTagPercents = userTagPercents;
    }

    public Set<Report> getReports() {
        return reports;
    }

    public void setReports(Set<Report> reports) {
        this.reports = reports;
    }

    // ───────────────────────────────────────────────
    // Удобные методы для связей (рекомендуется)
    // ───────────────────────────────────────────────

    public void addUserTagPercent(UserTagPercent utp) {
        this.userTagPercents.add(utp);
        utp.setUser(this);
    }

    public void removeUserTagPercent(UserTagPercent utp) {
        this.userTagPercents.remove(utp);
        utp.setUser(null);
    }

    public void addReport(Report report) {
        this.reports.add(report);
        report.setUser(this);
    }

    public void removeReport(Report report) {
        this.reports.remove(report);
        report.setUser(null);
    }

    // ───────────────────────────────────────────────
    // Автоматическая установка даты регистрации
    // ───────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        if (this.registrationDate == null) {
            this.registrationDate = new Date();
        }
    }
}