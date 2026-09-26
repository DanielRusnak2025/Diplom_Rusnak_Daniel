package org.example.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.example.Article;
import org.example.ArticleTagPercent;
import org.example.NotificationFrequency;
import org.example.User;
import org.example.service.ArticleService;
import org.example.service.ReportService;
import org.example.service.TagPercentService;
import org.example.service.UserService;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Главный экран для обычных пользователей.
 * Персонализированная лента статей с фильтрами, скорингом и кнопками оценки.
 */
public class MainScreenController implements Initializable {

    // ═══ FXML ═══
    @FXML private Button    languageButton;
    @FXML private Button    newsroomButton;
    @FXML private Button    categoriesButton;
    @FXML private Button    regionButton;
    @FXML private Button    searchToggleButton;
    @FXML private Button    settingsButton;
    @FXML private Button    logoutSidebarButton;
    @FXML private Button    historyButton;
    @FXML private Button    resetButton;
    @FXML private Button    enterButton;
    @FXML private Button    notifFreqButton;
    @FXML private HBox      searchRow;
    @FXML private TextField searchField;
    @FXML private Label     foundLabel;
    @FXML private Label     popularNotifLabel;
    @FXML private HBox      newArticlesBanner;
    @FXML private Label     newArticlesBannerLabel;
    @FXML private ListView<Article> articlesList;
    @FXML private StackPane userIconPane;
    @FXML private VBox      settingsOverlay;
    @FXML private VBox      logoutOverlay;

    // ═══ СЕРВИСЫ ═══
    private final ArticleService    articleService    = new ArticleService();
    private final ReportService     reportService     = new ReportService();
    private final TagPercentService tagPercentService = new TagPercentService();
    private final UserService       userService       = new UserService();

    // ═══ СОСТОЯНИЕ ═══
    private User    currentUser;
    private Article ratingTarget; // статья, по которой показываем кнопки реакции

    /** articleId → rating (1/0/-1/-2) — оценки текущей сессии (для подсветки кнопок) */
    private final Map<Integer, Integer> ratedArticles = new HashMap<>();

    /** ID статей, уже оценённых пользователем (из БД + текущая сессия) — для фильтрации */
    private Set<Integer> ratedArticleIdsFromDB = new HashSet<>();

    /** Персональный скор каждой статьи: articleId → score (-100..+100) */
    private final Map<Integer, Integer> scoreCache = new HashMap<>();

    /** Процент лайков для каждой статьи: articleId → likePercent (0..100) */
    private final Map<Integer, Integer> likePercentCache = new HashMap<>();

    /** Порог популярности — статья считается популярной, если likePercent > POPULAR_THRESHOLD */
    private static final int POPULAR_THRESHOLD = 50;

    private static final int FEED_LIMIT = 100;

    /** Максимальный ID статьи на момент последней загрузки — для определения новых */
    private int maxLoadedArticleId = 0;

    /** Фоновый поллер для проверки новых статей */
    private ScheduledExecutorService poller;

    private List<Article> allArticles     = new ArrayList<>();
    private List<Article> historyArticles = new ArrayList<>();

    /** articleId → rating пользователя — загружается при входе в режим истории */
    private Map<Integer, Integer> userRatingsMap = new HashMap<>();

    private boolean showHistoryMode = false;

    private String filterLanguage  = null;
    private String filterNewsroom  = null;
    private String filterCategory  = null;
    private String filterRegion    = null;

    // ═══════════════════════════════════════════════════════════
    // ИНИЦИАЛИЗАЦИЯ
    // ═══════════════════════════════════════════════════════════

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupListView();
    }

    /** Вызывается из LoginController сразу после load(). */
    public void setCurrentUser(User user) {
        this.currentUser = user;
        if (userIconPane != null && user != null) {
            Circle bg = new Circle(13, Color.web("#1e3330"));
            Label initial = new Label(user.getNick().substring(0, 1).toUpperCase());
            initial.setStyle("-fx-text-fill: #D4C398; -fx-font-size: 13; -fx-font-weight: bold;");
            userIconPane.getChildren().setAll(bg, initial);
        }
        if (user != null) {
            // Загружаем ID статей, оценённых ранее (из БД)
            ratedArticleIdsFromDB = reportService.getRatedArticleIds(user);
        }
        loadArticles();
        startPolling();
    }

    // ── Настройки частоты уведомлений ──────────────────────────

    /**
     * Порог лайков (%) в зависимости от настройки пользователя:
     * often → 15%, rarely → 50%, never → 75%.
     */
    private int getNotifThreshold() {
        if (currentUser == null) return NotificationFrequency.RARELY.getLikeThreshold();
        return NotificationFrequency.fromString(currentUser.getNotificationFrequency()).getLikeThreshold();
    }

    /**
     * Интервал polling в секундах — определяется через NotificationFrequency enum.
     */
    private int getNotifPollIntervalSec() {
        if (currentUser == null) return NotificationFrequency.RARELY.getPollIntervalSec();
        return NotificationFrequency.fromString(currentUser.getNotificationFrequency()).getPollIntervalSec();
    }

    /**
     * Считает новые статьи (id > maxLoadedArticleId), у которых процент лайков ≥ порогу.
     * Статьи без оценок (likePercent = 0) не учитываются.
     */
    private int countNotifiableNewArticles() {
        List<Integer> newIds = articleService.getArticleIdsNewerThan(maxLoadedArticleId);
        if (newIds.isEmpty()) return 0;
        int threshold = getNotifThreshold();
        Map<Integer, Integer> likeMap = reportService.getLikePercentageMap(newIds);
        return (int) likeMap.values().stream().filter(pct -> pct >= threshold).count();
    }

    /** Запускает фоновый поллер с интервалом, зависящим от настройки уведомлений. */
    private void startPolling() {
        stopPolling();
        int intervalSec = getNotifPollIntervalSec();
        poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "article-poller");
            t.setDaemon(true);
            return t;
        });
        poller.scheduleAtFixedRate(() -> {
            if (articlesList.getScene() == null || !articlesList.getScene().getWindow().isShowing()) {
                stopPolling();
                return;
            }
            try {
                int notifiable = countNotifiableNewArticles();
                Platform.runLater(() -> showNewArticlesBanner(notifiable));
            } catch (Exception e) {
                System.err.println("[Poller] Ошибка: " + e.getMessage());
            }
        }, intervalSec, intervalSec, TimeUnit.SECONDS);
        System.out.println("[Poller] Запущен: интервал=" + intervalSec + "s, порог лайков=" + getNotifThreshold() + "%");
    }

    /** Останавливает поллер, если он запущен. */
    private void stopPolling() {
        if (poller != null && !poller.isShutdown()) {
            poller.shutdownNow();
        }
        poller = null;
    }

    /** Показывает или скрывает баннер новых статей. */
    private void showNewArticlesBanner(int newCount) {
        if (newArticlesBanner == null) return;
        if (newCount > 0) {
            newArticlesBannerLabel.setText("🔔  " + newCount
                    + (newCount == 1 ? " new article" : " new articles") + " available");
            newArticlesBanner.setVisible(true);
            newArticlesBanner.setManaged(true);
        } else {
            newArticlesBanner.setVisible(false);
            newArticlesBanner.setManaged(false);
        }
    }

    /** Кнопка "↻ Load" на баннере — перезагружает ленту и скрывает баннер. */
    @FXML private void onRefreshNewArticles(ActionEvent event) {
        showNewArticlesBanner(0); // сразу скрываем
        loadArticles();
    }

    // ═══════════════════════════════════════════════════════════
    // ЗАГРУЗКА И СКОРИНГ
    // ═══════════════════════════════════════════════════════════

    private void loadArticles() {
        try {
            allArticles = articleService.getRecentArticlesWithTags(FEED_LIMIT);

            // Запоминаем максимальный ID — всё, что придёт с бо́льшим ID, будет "новым"
            maxLoadedArticleId = allArticles.stream()
                    .mapToInt(Article::getId)
                    .max()
                    .orElse(maxLoadedArticleId);

            // Скрываем баннер — только что загрузили актуальные данные
            showNewArticlesBanner(0);

            // Вычисляем скор для каждой статьи (1 запрос к БД для тегов пользователя)
            if (currentUser != null) {
                Map<Integer, Integer> userTagMap = tagPercentService.getUserTagMap(currentUser);
                scoreCache.clear();
                for (Article a : allArticles) {
                    scoreCache.put(a.getId(), tagPercentService.calculateArticleScore(a, userTagMap));
                }
            }

            // Загружаем процент лайков для всех статей (1 запрос к БД)
            likePercentCache.clear();
            if (!allArticles.isEmpty()) {
                List<Integer> ids = allArticles.stream()
                        .map(Article::getId)
                        .collect(Collectors.toList());
                likePercentCache.putAll(reportService.getLikePercentageMap(ids));
            }

            applyFilters();
        } catch (Exception e) {
            System.err.println("Ошибка загрузки статей: " + e.getMessage());
            foundLabel.setText("Ошибка загрузки");
        }
    }

    private void applyFilters() {
        String search = searchField.getText().trim().toLowerCase();

        // В режиме истории — источник оценённые статьи; иначе — обычная лента без оценённых
        List<Article> source = showHistoryMode
                ? historyArticles
                : allArticles.stream()
                        .filter(a -> !ratedArticleIdsFromDB.contains(a.getId()))
                        .collect(Collectors.toList());

        List<Article> result = source.stream()
                .filter(a -> filterLanguage == null
                        || filterLanguage.equalsIgnoreCase(a.getArticleLanguage()))
                .filter(a -> filterNewsroom == null
                        || filterNewsroom.equalsIgnoreCase(a.getPublisher()))
                .filter(a -> filterRegion == null
                        || filterRegion.equalsIgnoreCase(a.getArticleRegion()))
                .filter(a -> filterCategory == null || hasTag(a, filterCategory))
                .filter(a -> search.isEmpty()
                        || containsCI(a.getTitle(), search)
                        || containsCI(a.getPublisher(), search))
                .sorted((a, b) -> Integer.compare(
                        scoreCache.getOrDefault(b.getId(), 0),
                        scoreCache.getOrDefault(a.getId(), 0)))
                .collect(Collectors.toList());

        articlesList.setItems(FXCollections.observableArrayList(result));

        String filters = buildFilterLabel();
        String prefix  = showHistoryMode ? "history: " : "found: ";
        foundLabel.setText(prefix + result.size()
                + (filters.isEmpty() ? "" : "  •  " + filters));

        // Считаем популярные статьи в текущей выборке и показываем/скрываем баннер
        if (!showHistoryMode && popularNotifLabel != null) {
            long popularCount = result.stream()
                    .filter(a -> likePercentCache.getOrDefault(a.getId(), 0) > POPULAR_THRESHOLD)
                    .count();
            if (popularCount > 0) {
                popularNotifLabel.setText("🔥  " + popularCount
                        + (popularCount == 1 ? " article" : " articles")
                        + " liked by 50%+ readers — check them out!");
                popularNotifLabel.setVisible(true);
                popularNotifLabel.setManaged(true);
            } else {
                popularNotifLabel.setVisible(false);
                popularNotifLabel.setManaged(false);
            }
        } else if (popularNotifLabel != null) {
            popularNotifLabel.setVisible(false);
            popularNotifLabel.setManaged(false);
        }
    }

    private String buildFilterLabel() {
        List<String> parts = new ArrayList<>();
        if (filterLanguage != null) parts.add("lang:" + filterLanguage);
        if (filterNewsroom != null) parts.add("src:" + filterNewsroom);
        if (filterCategory != null) parts.add("tag:" + filterCategory);
        if (filterRegion    != null) parts.add("region:" + filterRegion);
        return String.join("  ", parts);
    }

    // ═══════════════════════════════════════════════════════════
    // СПИСОК СТАТЕЙ
    // ═══════════════════════════════════════════════════════════

    private void setupListView() {
        articlesList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Article article, boolean empty) {
                super.updateItem(article, empty);
                // Prevent the cell from reporting a preferred width wider than the ListView
                setPrefWidth(0);
                if (empty || article == null) {
                    setGraphic(null);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }
                boolean showButtons = article.equals(ratingTarget);
                VBox card = buildCard(article, showButtons);
                card.prefWidthProperty().bind(lv.widthProperty().subtract(20));
                setGraphic(card);
                setText(null);
                setStyle("-fx-background-color: transparent; -fx-padding: 4 0 4 0;");
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    // ПОСТРОЕНИЕ КАРТОЧКИ
    // ═══════════════════════════════════════════════════════════

    private VBox buildCard(Article article, boolean showActionButtons) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(12, 12, 10, 14));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle(
                "-fx-background-color: #EDE0C1;" +
                "-fx-background-radius: 16;" +
                "-fx-border-radius: 16;" +
                "-fx-border-color: #294946;" +
                "-fx-border-width: 2;" +
                "-fx-cursor: hand;"
        );

        // ── Заголовок + языковой badge ──
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.TOP_LEFT);

        Label titleLbl = new Label(nvl(article.getTitle()));
        titleLbl.setWrapText(true);
        titleLbl.setMinWidth(0);
        titleLbl.setMaxWidth(Double.MAX_VALUE);
        titleLbl.setStyle(
                "-fx-font-family: 'Inter', 'Arial'; " +
                "-fx-font-size: 16; -fx-font-weight: 800; -fx-text-fill: #294946;"
        );
        HBox.setHgrow(titleLbl, Priority.ALWAYS);

        StackPane badge = buildLangBadge(article.getArticleLanguage());
        topRow.getChildren().addAll(titleLbl, badge);

        // ── Значок популярности — если лайков > POPULAR_THRESHOLD ──
        int likePercent = likePercentCache.getOrDefault(article.getId(), 0);
        boolean isPopular = likePercent > POPULAR_THRESHOLD;
        if (isPopular) {
            card.setStyle(
                    "-fx-background-color: #EDE0C1;" +
                    "-fx-background-radius: 16;" +
                    "-fx-border-radius: 16;" +
                    "-fx-border-color: #2E7D32;" +
                    "-fx-border-width: 2.5;" +
                    "-fx-cursor: hand;"
            );
            Label popularBadge = new Label("🔥 " + likePercent + "% liked");
            popularBadge.setStyle(
                    "-fx-background-color: #2E7D32;" +
                    "-fx-text-fill: #EDE0C1;" +
                    "-fx-font-size: 10;" +
                    "-fx-font-weight: bold;" +
                    "-fx-background-radius: 20;" +
                    "-fx-padding: 2 8 2 8;"
            );
            card.getChildren().add(0, popularBadge);
        }

        // ── Темы ──
        Label mainLbl = new Label(getTagByRank(article, 0));
        mainLbl.setStyle(
                "-fx-font-family: 'Inter', 'Arial'; " +
                "-fx-font-size: 14; -fx-font-weight: 800; -fx-text-fill: #294946;"
        );

        Label secLbl = new Label(getTagByRank(article, 1));
        secLbl.setStyle(
                "-fx-font-family: 'Inter', 'Arial'; " +
                "-fx-font-size: 14; -fx-font-weight: 800; -fx-text-fill: #294946;"
        );

        // ── Персональный скор ──
        int score = scoreCache.getOrDefault(article.getId(), 0);
        Label scoreLbl = new Label(formatScore(score));
        String scoreColor = score > 0 ? "#2E7D32" : score < 0 ? "#C0392B" : "#888888";
        scoreLbl.setStyle(
                "-fx-font-family: 'Arial Bold'; " +
                "-fx-font-size: 11; -fx-font-weight: bold; " +
                "-fx-text-fill: " + scoreColor + ";"
        );

        card.getChildren().addAll(topRow, mainLbl, secLbl, scoreLbl);

        if (showHistoryMode) {
            // В режиме истории — значок оценки вместо кнопок
            card.getChildren().add(buildRatingBadge(userRatingsMap.get(article.getId())));
            card.setOnMouseClicked(e -> openInBrowser(article));
        } else {
            // В обычном режиме — кнопки реакции после клика
            if (showActionButtons) {
                card.getChildren().add(buildActionRow(article));
            }
            card.setOnMouseClicked(e -> onCardClick(article));
        }

        return card;
    }

    private String formatScore(int score) {
        if (score > 0) return "Match: +" + score + "%";
        if (score < 0) return "Match: " + score + "%";
        return "Match: 0%";
    }

    private static final java.util.Map<String, String> LANG_FLAG = java.util.Map.ofEntries(
            java.util.Map.entry("english",    "gb"),
            java.util.Map.entry("eng",        "gb"),
            java.util.Map.entry("engl",       "gb"),
            java.util.Map.entry("en",         "gb"),
            java.util.Map.entry("german",     "de"),
            java.util.Map.entry("ger",        "de"),
            java.util.Map.entry("deu",        "de"),
            java.util.Map.entry("de",         "de"),
            java.util.Map.entry("russian",    "ru"),
            java.util.Map.entry("rus",        "ru"),
            java.util.Map.entry("ru",         "ru"),
            java.util.Map.entry("hebrew",     "il"),
            java.util.Map.entry("heb",        "il"),
            java.util.Map.entry("he",         "il"),
            java.util.Map.entry("arabic",     "sa"),
            java.util.Map.entry("ara",        "sa"),
            java.util.Map.entry("ar",         "sa"),
            java.util.Map.entry("french",     "fr"),
            java.util.Map.entry("fra",        "fr"),
            java.util.Map.entry("fr",         "fr"),
            java.util.Map.entry("spanish",    "es"),
            java.util.Map.entry("spa",        "es"),
            java.util.Map.entry("es",         "es"),
            java.util.Map.entry("romanian",   "ro"),
            java.util.Map.entry("rom",        "ro"),
            java.util.Map.entry("ro",         "ro"),
            java.util.Map.entry("ukrainian",  "ua"),
            java.util.Map.entry("ukr",        "ua"),
            java.util.Map.entry("uk",         "ua"),
            java.util.Map.entry("italian",    "it"),
            java.util.Map.entry("ita",        "it"),
            java.util.Map.entry("it",         "it"),
            java.util.Map.entry("portuguese", "pt"),
            java.util.Map.entry("por",        "pt"),
            java.util.Map.entry("pt",         "pt"),
            java.util.Map.entry("chinese",    "cn"),
            java.util.Map.entry("zho",        "cn"),
            java.util.Map.entry("chi",        "cn"),
            java.util.Map.entry("zh",         "cn"),
            java.util.Map.entry("japanese",   "jp"),
            java.util.Map.entry("jpn",        "jp"),
            java.util.Map.entry("ja",         "jp")
    );

    private StackPane buildLangBadge(String language) {
        String code = (language != null && !language.isBlank())
                ? LANG_FLAG.getOrDefault(language.trim().toLowerCase(), null)
                : null;

        Circle circle = new Circle(25, Color.web("#294946"));

        StackPane pane = new StackPane(circle);
        pane.setMinSize(50, 50);
        pane.setMaxSize(50, 50);

        if (code != null) {
            java.io.InputStream is = getClass().getResourceAsStream("/flags/" + code + ".png");
            if (is != null) {
                javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(
                        new javafx.scene.image.Image(is));
                iv.setFitWidth(32);
                iv.setFitHeight(24);
                iv.setPreserveRatio(true);
                pane.getChildren().add(iv);
                return pane;
            }
        }

        Label lbl = new Label(code != null ? code.toUpperCase() : "?");
        lbl.setStyle("-fx-font-family: 'Inter', 'Arial'; -fx-text-fill: #D4C398; -fx-font-size: 14; -fx-font-weight: 800;");
        pane.getChildren().add(lbl);
        return pane;
    }

    private HBox buildActionRow(Article article) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 0, 0, 0));

        Integer existingRating = ratedArticles.get(article.getId());
        boolean alreadyRated   = existingRating != null;

        Button likeBtn    = ratingButton("👍", "#2E7D32", article, 1,  existingRating, alreadyRated);
        Button neutralBtn = ratingButton("✓",  "#D4AC0D", article, 0,  existingRating, alreadyRated);
        Button dislikeBtn = ratingButton("👎", "#D4C398", article, -1, existingRating, alreadyRated);
        Button reportBtn  = ratingButton("⚠",  "#C0392B", article, -2, existingRating, alreadyRated);

        row.getChildren().addAll(likeBtn, neutralBtn, dislikeBtn, reportBtn);
        return row;
    }

    /** Значок с оценкой пользователя — показывается в режиме истории. */
    private HBox buildRatingBadge(Integer rating) {
        String icon, color;
        if (rating == null)    { icon = "? unknown";    color = "#888888"; }
        else if (rating == 1)  { icon = "👍 Liked";     color = "#2E7D32"; }
        else if (rating == 0)  { icon = "✓ Neutral";    color = "#D4AC0D"; }
        else if (rating == -1) { icon = "👎 Disliked";  color = "#C0392B"; }
        else                   { icon = "⚠ Reported";   color = "#C0392B"; }

        Label lbl = new Label(icon);
        lbl.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        HBox row = new HBox(lbl);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 0, 0));
        return row;
    }

    private Button ratingButton(String icon, String bgColor, Article article, int rating,
                                Integer existingRating, boolean alreadyRated) {
        Button btn = new Button(icon);

        boolean isActive = alreadyRated && existingRating != null && existingRating == rating;
        String opacity   = alreadyRated && !isActive ? "-fx-opacity: 0.35;" : "";
        String border    = isActive ? "-fx-border-color: #FFFFFF; -fx-border-width: 2; -fx-border-radius: 45;" : "";

        btn.setStyle(
                "-fx-background-color: " + bgColor + "; " +
                "-fx-background-radius: 45; " +
                "-fx-text-fill: #294946; " +
                "-fx-font-size: 15; " +
                "-fx-min-width: 35; -fx-min-height: 35; " +
                "-fx-cursor: hand; " +
                border + opacity
        );
        btn.setDisable(alreadyRated);

        btn.setOnAction(e -> {
            if (rating == -2) {
                onReport(article);
            } else {
                doRate(article, rating);
            }
        });
        return btn;
    }

    // ═══════════════════════════════════════════════════════════
    // КЛИК ПО КАРТОЧКЕ
    // ═══════════════════════════════════════════════════════════

    private void onCardClick(Article article) {
        closeOverlays();
        if (article.equals(ratingTarget)) {
            ratingTarget = null;
        } else {
            openInBrowser(article);
            ratingTarget = article;
        }
        articlesList.refresh();
    }

    // ═══════════════════════════════════════════════════════════
    // РЕАКЦИИ
    // ═══════════════════════════════════════════════════════════

    private void doRate(Article article, int direction) {
        if (currentUser == null) return;
        if (ratedArticleIdsFromDB.contains(article.getId())) return;
        try {
            reportService.upsertRating(article, currentUser, direction);
            tagPercentService.adjustUserTagWeights(article, currentUser, direction);
            ratedArticles.put(article.getId(), direction);
            ratedArticleIdsFromDB.add(article.getId()); // скрываем из ленты
            ratingTarget = null;
            refreshScores(); // пересчитываем скоры на основе новых весов тегов
            applyFilters();
        } catch (Exception e) {
            System.err.println("Ошибка оценки: " + e.getMessage());
        }
    }

    /** Перезагружает веса тегов пользователя из БД и пересчитывает scoreCache. */
    private void refreshScores() {
        if (currentUser == null) return;
        Map<Integer, Integer> userTagMap = tagPercentService.getUserTagMap(currentUser);
        scoreCache.clear();
        for (Article a : allArticles) {
            scoreCache.put(a.getId(), tagPercentService.calculateArticleScore(a, userTagMap));
        }
    }

    private void onReport(Article article) {
        if (currentUser == null) return;
        if (ratedArticleIdsFromDB.contains(article.getId())) return;
        try {
            reportService.upsertRating(article, currentUser, -2);
            ratedArticles.put(article.getId(), -2);
            ratedArticleIdsFromDB.add(article.getId());
            ratingTarget = null;
            applyFilters();
            showAlert(Alert.AlertType.INFORMATION,
                    "Жалоба отправлена на модерацию:\n\"" + nvl(article.getTitle()) + "\"");
        } catch (Exception e) {
            System.err.println("Ошибка репорта: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Ошибка при отправке жалобы");
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ФИЛЬТРЫ
    // ═══════════════════════════════════════════════════════════

    @FXML private void onLanguageFilter(ActionEvent e) {
        List<String> opts = allArticles.stream()
                .map(Article::getArticleLanguage)
                .filter(Objects::nonNull).distinct().sorted()
                .collect(Collectors.toList());
        showFilterMenu((Button) e.getSource(), opts, val -> {
            filterLanguage = val;
            languageButton.setText(val != null ? val + " ▶" : "Language ▶");
            applyFilters();
        });
    }

    @FXML private void onNewsroomFilter(ActionEvent e) {
        List<String> opts = allArticles.stream()
                .map(Article::getPublisher)
                .filter(Objects::nonNull).distinct().sorted()
                .collect(Collectors.toList());
        showFilterMenu((Button) e.getSource(), opts, val -> {
            filterNewsroom = val;
            newsroomButton.setText(val != null ? val + " ▶" : "Newsroom ▶");
            applyFilters();
        });
    }

    @FXML private void onCategoriesFilter(ActionEvent e) {
        List<String> opts = allArticles.stream()
                .flatMap(a -> a.getArticleTagPercents().stream())
                .filter(atp -> atp.getTag() != null)
                .map(atp -> atp.getTag().getName())
                .distinct().sorted()
                .collect(Collectors.toList());
        showFilterMenu((Button) e.getSource(), opts, val -> {
            filterCategory = val;
            categoriesButton.setText(val != null ? val + " ▶" : "Categories ▶");
            applyFilters();
        });
    }

    @FXML private void onRegionFilter(ActionEvent e) {
        List<String> opts = allArticles.stream()
                .map(Article::getArticleRegion)
                .filter(Objects::nonNull).distinct().sorted()
                .collect(Collectors.toList());
        showFilterMenu((Button) e.getSource(), opts, val -> {
            filterRegion = val;
            regionButton.setText(val != null ? val + " ▶" : "Region ▶");
            applyFilters();
        });
    }

    @FXML private void onResetFilters(ActionEvent event) {
        filterLanguage = filterNewsroom = filterCategory = filterRegion = null;
        languageButton.setText("Language ▶");
        newsroomButton.setText("Newsroom ▶");
        categoriesButton.setText("Categories ▶");
        regionButton.setText("Region ▶");
        searchField.clear();
        ratingTarget = null;
        // Выходим из режима истории если был активен
        if (showHistoryMode) {
            showHistoryMode = false;
            historyArticles.clear();
            userRatingsMap.clear();
            historyButton.setStyle(
                    "-fx-background-color: transparent; -fx-cursor: hand; " +
                    "-fx-text-fill: #D4C398; -fx-font-size: 18; -fx-font-weight: bold; -fx-padding: 0;");
        }
        applyFilters();
    }

    @FXML private void onToggleSearch(ActionEvent event) {
        boolean show = !searchRow.isVisible();
        searchRow.setVisible(show);
        searchRow.setManaged(show);
        if (show) searchField.requestFocus();
        else { searchField.clear(); applyFilters(); }
    }

    @FXML private void onSearch(ActionEvent event) {
        applyFilters();
    }

    @FXML private void onHistory(ActionEvent event) {
        closeOverlays();
        showHistoryMode = !showHistoryMode;
        ratingTarget = null;

        if (showHistoryMode) {
            // Загружаем статьи из истории и оценки пользователя
            historyArticles = ratedArticleIdsFromDB.isEmpty()
                    ? new ArrayList<>()
                    : articleService.getArticlesByIds(ratedArticleIdsFromDB);

            // Считаем скор для статей истории, которых нет в кэше
            if (currentUser != null && !historyArticles.isEmpty()) {
                Map<Integer, Integer> userTagMap = tagPercentService.getUserTagMap(currentUser);
                for (Article a : historyArticles) {
                    scoreCache.computeIfAbsent(a.getId(),
                            id -> tagPercentService.calculateArticleScore(a, userTagMap));
                }
            }

            userRatingsMap = currentUser != null
                    ? reportService.getUserRatingsMap(currentUser)
                    : new HashMap<>();

            // Подсвечиваем кнопку как активную
            historyButton.setStyle(
                    "-fx-background-color: #D4C398; -fx-cursor: hand; " +
                    "-fx-text-fill: #294946; -fx-font-size: 18; -fx-font-weight: bold; " +
                    "-fx-background-radius: 45; -fx-padding: 0; " +
                    "-fx-min-width: 27; -fx-min-height: 27;");
        } else {
            historyArticles.clear();
            userRatingsMap.clear();

            // Возвращаем стиль по умолчанию
            historyButton.setStyle(
                    "-fx-background-color: transparent; -fx-cursor: hand; " +
                    "-fx-text-fill: #D4C398; -fx-font-size: 18; -fx-font-weight: bold; -fx-padding: 0;");
        }

        applyFilters();
    }

    private void showFilterMenu(Button anchor, List<String> options,
                                 java.util.function.Consumer<String> onSelect) {
        ContextMenu menu = new ContextMenu();
        MenuItem all = new MenuItem("— All —");
        all.setOnAction(ev -> onSelect.accept(null));
        menu.getItems().add(all);
        for (String opt : options) {
            MenuItem item = new MenuItem(opt);
            item.setOnAction(ev -> onSelect.accept(opt));
            menu.getItems().add(item);
        }
        menu.show(anchor, Side.BOTTOM, 0, 4);
    }

    // ═══════════════════════════════════════════════════════════
    // ОВЕРЛЕЙ: НАСТРОЙКИ
    // ═══════════════════════════════════════════════════════════

    @FXML private void onSettings(ActionEvent event) {
        if (settingsOverlay.isVisible()) {
            settingsOverlay.setVisible(false);
        } else {
            closeOverlays();
            // Синхронизируем кнопку частоты уведомлений с текущим значением пользователя
            if (currentUser != null && notifFreqButton != null) {
                notifFreqButton.setText(currentUser.getNotificationFrequency() + " ▶");
            }
            settingsOverlay.setVisible(true);
        }
    }

    @FXML private void onNotifFreq(ActionEvent event) {
        ContextMenu menu = new ContextMenu();
        for (NotificationFrequency freq : NotificationFrequency.values()) {
            String val = freq.getValue();
            MenuItem item = new MenuItem(val);
            item.setOnAction(ev -> {
                currentUser.setNotificationFrequency(val);
                userService.updateNotificationFrequency(currentUser.getNick(), val);
                notifFreqButton.setText(val + " ▶");
                startPolling();
            });
            menu.getItems().add(item);
        }
        menu.show((Button) event.getSource(), Side.BOTTOM, 0, 4);
    }

    @FXML private void onAdd2FA(ActionEvent event) {
        showAlert(Alert.AlertType.INFORMATION, "Add 2FA — coming soon");
    }

    @FXML private void onChangePassword(ActionEvent event) {
        showAlert(Alert.AlertType.INFORMATION, "Change Password — coming soon");
    }

    // ═══════════════════════════════════════════════════════════
    // ОВЕРЛЕЙ: ВЫХОД
    // ═══════════════════════════════════════════════════════════

    @FXML private void onLogout(ActionEvent event) {
        if (logoutOverlay.isVisible()) {
            logoutOverlay.setVisible(false);
        } else {
            closeOverlays();
            logoutOverlay.setVisible(true);
        }
    }

    @FXML private void onConfirmLogout(ActionEvent event) {
        stopPolling();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Login.fxml"));
            Parent root = loader.load();
            Stage loginStage = new Stage();
            loginStage.setTitle("NewsDove");
            loginStage.setScene(new Scene(root, 520, 932));
            loginStage.setResizable(false);
            loginStage.show();
            ((Stage) logoutSidebarButton.getScene().getWindow()).close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML private void onCancelLogout(ActionEvent event) {
        logoutOverlay.setVisible(false);
    }

    private void closeOverlays() {
        if (settingsOverlay != null) settingsOverlay.setVisible(false);
        if (logoutOverlay   != null) logoutOverlay.setVisible(false);
    }

    // ═══════════════════════════════════════════════════════════
    // УТИЛИТЫ
    // ═══════════════════════════════════════════════════════════

    private void openInBrowser(Article article) {
        if (article.getLink() == null || article.getLink().isBlank()) return;
        try {
            if (java.awt.Desktop.isDesktopSupported())
                java.awt.Desktop.getDesktop().browse(new URI(article.getLink()));
        } catch (Exception e) {
            System.err.println("Браузер: " + e.getMessage());
        }
    }

    private String getTagByRank(Article article, int rank) {
        if (article.getArticleTagPercents() == null || article.getArticleTagPercents().isEmpty())
            return "";
        List<ArticleTagPercent> sorted = article.getArticleTagPercents().stream()
                .filter(t -> t.getTag() != null)
                .sorted(Comparator.comparingInt(ArticleTagPercent::getPercent).reversed())
                .toList();
        return sorted.size() > rank ? sorted.get(rank).getTag().getName() : "";
    }

    private boolean hasTag(Article article, String tagName) {
        if (article.getArticleTagPercents() == null) return false;
        return article.getArticleTagPercents().stream()
                .filter(atp -> atp.getTag() != null)
                .anyMatch(atp -> tagName.equalsIgnoreCase(atp.getTag().getName()));
    }

    private boolean containsCI(String text, String search) {
        return text != null && text.toLowerCase().contains(search);
    }

    private void showAlert(Alert.AlertType type, String msg) {
        Alert a = new Alert(type);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private String nvl(String s) { return s != null ? s : ""; }
}
