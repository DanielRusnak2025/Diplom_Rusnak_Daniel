package org.example;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "user")
public class User {

    @Id
    @Column(length = 50)
    private String nick;

    @Column(length = 255, nullable = false)
    private String password;

    // Рекомендую переименовать, например в oldPassword / previousPassword / tempPassword
    // "firstPassword" звучит странно в контексте постоянного хранения
    @Column(length = 255)
    private String firstPassword;

    @Column(name = "registration_date")
    @Temporal(TemporalType.DATE)
    private Date registrationDate;

    @Column(length = 100, unique = true)
    private String email;

    @Column(name = "preferred_region", length = 100)
    private String preferredRegion;

    @Column(name = "preferred_language", length = 50)
    private String preferredLanguage;

    @Column(name = "is_admin")
    private boolean isAdmin;

    @Column(name = "read_articles_count")
    private int readArticlesCount;

    @Column(name = "notification_frequency", length = 10)
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
        this.isAdmin = false;
    }

    // ───────────────────────────────────────────────
    // Геттеры и сеттеры
    // ───────────────────────────────────────────────

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

    public String getFirstPassword() {
        return firstPassword;
    }

    public void setFirstPassword(String firstPassword) {
        this.firstPassword = firstPassword;
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

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
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