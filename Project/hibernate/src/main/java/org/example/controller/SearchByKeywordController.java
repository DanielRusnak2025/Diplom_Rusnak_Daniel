package org.example.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.geometry.Side;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.*;
import org.example.NotificationFrequency;
import org.example.service.*;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Контроллер для SearchByKeyword.fxml
 * Отвечает за: поиск статей, отображение и редактирование статей
 */
public class SearchByKeywordController implements Initializable {

    // ═══════════════════════════════════════════════════════════
    // ПОИСКОВАЯ ПАНЕЛЬ
    // ═══════════════════════════════════════════════════════════
    @FXML
    private TextField keywordSearchField;
    @FXML
    private DatePicker searchDatePicker;
    @FXML
    private Button keywordSearchButton;
    @FXML
    private Button usernameSearchButton;
    @FXML
    private Button reportsSearchButton;

    // ═══════════════════════════════════════════════════════════
    // СПИСКИ
    // ═══════════════════════════════════════════════════════════
    @FXML
    private ListView<Article> articlesListView;
    @FXML
    private ListView<User> blocksListView;
    @FXML
    private ListView<ArticleTagPercent> tagsListView;
    @FXML
    private ListView<Report> gradeListView;
    @FXML
    private PieChart gradePieChart;

    // ═══════════════════════════════════════════════════════════
    // ПОЛЯ РЕДАКТИРОВАНИЯ СТАТЬИ
    // ═══════════════════════════════════════════════════════════
    @FXML
    private TextField articleTitleField;
    @FXML
    private TextField articleLinkField;
    @FXML
    private TextField newsRoomField;
    @FXML
    private TextField languageField;
    @FXML
    private TextField regionField;
    @FXML
    private TextField publicationDateField;

    // ═══════════════════════════════════════════════════════════
    // КНОПКИ ДЕЙСТВИЙ
    // ═══════════════════════════════════════════════════════════
    @FXML
    private Button settingsButton;
    @FXML
    private Button logoutButton;
    @FXML private VBox   settingsOverlay;
    @FXML private VBox   logoutOverlay;
    @FXML private Button notifFreqButton;
    @FXML
    private Button saveButton;
    @FXML
    private Button deleteButton;

    // ═══════════════════════════════════════════════════════════
    // СЕРВИСЫ
    // ═══════════════════════════════════════════════════════════
    private final ArticleService articleService = new ArticleService();
    private final TagPercentService tagPercentService = new TagPercentService();
    private final ReportService reportService = new ReportService();

    private Article selectedArticle;

    // Singleton — нужен EmailScheduler'у для вызова refreshArticles()
    private static SearchByKeywordController instance;

    public static SearchByKeywordController getInstance() {
        return instance;
    }

    // ═══════════════════════════════════════════════════════════
    // ИНИЦИАЛИЗАЦИЯ
    // ═══════════════════════════════════════════════════════════
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        instance = this;
        System.out.println("🚀 SearchByKeywordController инициализирован");

        setupArticlesListView();
        setupBlocksListView();
        setupTagsListView();
        setupGradeListView();

        // Слушатель выбора статьи из списка
        articlesListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    selectedArticle = newVal;
                    showArticleDetails(newVal);
                }
        );

        loadAllArticles();
    }

    // ═══════════════════════════════════════════════════════════
    // НАСТРОЙКА ОТОБРАЖЕНИЯ СПИСКОВ
    // ═══════════════════════════════════════════════════════════

    private void setupArticlesListView() {
        articlesListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Article item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getTitle());
            }
        });
    }

    private void setupBlocksListView() {
        blocksListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getNick());
            }
        });
    }

    private void setupTagsListView() {
        tagsListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ArticleTagPercent item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }

                int p = item.getPercent();

                HBox box = new HBox(10);
                box.setAlignment(Pos.CENTER_LEFT);

                Label nameLabel = new Label(item.getTag() != null ? item.getTag().getName() : "?");
                nameLabel.setMaxWidth(Double.MAX_VALUE);
                nameLabel.setStyle("-fx-font-family:'Arial Bold';-fx-font-size:14;-fx-text-fill:#294946;");
                HBox.setHgrow(nameLabel, javafx.scene.layout.Priority.ALWAYS);

                ProgressBar bar = new ProgressBar(Math.max(0, Math.min(100, p)) / 100.0);
                bar.setMaxWidth(Double.MAX_VALUE);
                bar.setStyle("-fx-accent:" + (p >= 0 ? "#294946" : "#C0392B") + ";");
                HBox.setHgrow(bar, javafx.scene.layout.Priority.ALWAYS);

                String pctText = (p > 0 ? "+" : "") + p + "%";
                Label pct = new Label(pctText);
                pct.setStyle("-fx-font-family:'Arial Bold';-fx-font-size:14;-fx-text-fill:"
                        + (p >= 0 ? "#294946" : "#C0392B") + ";");

                box.getChildren().addAll(nameLabel, bar, pct);
                setGraphic(box);
                setText(null);
            }
        });
    }

    private void setupGradeListView() {
        gradeListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Report item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                String icon = switch (item.getRating()) {
                    case  1 -> "👍";
                    case  0 -> "😐";
                    case -1 -> "👎";
                    default  -> "?";
                };
                String nick   = item.getUser()  != null ? item.getUser().getNick() : "?";
                String reason = item.getReason() != null && !item.getReason().isBlank()
                        ? " — " + item.getReason() : "";
                setText(nick + ": " + icon + reason);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    // ЗАГРУЗКА СТАТЕЙ
    // ═══════════════════════════════════════════════════════════

    private void loadAllArticles() {
        try {
            List<Article> articles = articleService.getAllArticles();
            articlesListView.setItems(FXCollections.observableArrayList(articles));
            System.out.println("✅ Загружено статей: " + articles.size());
        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки статей: " + e.getMessage());
        }
    }

    /**
     * Вызывается из EmailScheduler после сохранения новой статьи через AI pipeline.
     */
    public void refreshArticles() {
        Platform.runLater(() -> {
            System.out.println("🔄 Обновление списка статей из pipeline...");
            loadAllArticles();
        });
    }

    // ═══════════════════════════════════════════════════════════
    // ПОИСК
    // ═══════════════════════════════════════════════════════════

    @FXML
    private void onKeywordSearch(ActionEvent event) {
        String keyword = keywordSearchField.getText().trim();
        LocalDate date  = searchDatePicker.getValue();

        if (keyword.isEmpty() && date == null) {
            loadAllArticles();
            return;
        }

        try {
            List<Article> results = (date != null)
                    ? articleService.searchArticles(keyword, date)
                    : articleService.searchArticlesByKeyword(keyword);

            articlesListView.setItems(FXCollections.observableArrayList(results));

            if (results.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "Статьи не найдены");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Ошибка поиска: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // НАВИГАЦИЯ
    // ═══════════════════════════════════════════════════════════

    /**
     * Переход на SearchByUsername.fxml (управление пользователями)
     */
    @FXML
    private void onSettings(ActionEvent event) {
        if (settingsOverlay.isVisible()) {
            settingsOverlay.setVisible(false);
        } else {
            logoutOverlay.setVisible(false);
            settingsOverlay.setVisible(true);
        }
    }

    @FXML private void onNotifFreq(ActionEvent event) {
        ContextMenu menu = new ContextMenu();
        for (NotificationFrequency freq : NotificationFrequency.values()) {
            String val = freq.getValue();
            MenuItem item = new MenuItem(val);
            item.setOnAction(ev -> notifFreqButton.setText(val + " ▶"));
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

    @FXML
    private void onLogout(ActionEvent event) {
        if (logoutOverlay.isVisible()) {
            logoutOverlay.setVisible(false);
        } else {
            settingsOverlay.setVisible(false);
            logoutOverlay.setVisible(true);
        }
    }

    @FXML private void onConfirmLogout(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Login.fxml"));
            Parent root = loader.load();
            Stage loginStage = new Stage();
            loginStage.setTitle("NewsDove");
            loginStage.setScene(new Scene(root, 520, 932));
            loginStage.setResizable(false);
            loginStage.show();
            ((Stage) logoutButton.getScene().getWindow()).close();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Ошибка выхода: " + e.getMessage());
        }
    }

    @FXML private void onCancelLogout(ActionEvent event) {
        logoutOverlay.setVisible(false);
    }

    @FXML
    private void onUsernameSearch(ActionEvent event) {
        navigateTo("/SearchByUsername.fxml", usernameSearchButton);
    }

    @FXML
    private void onReportsSearch(ActionEvent event) {
        navigateTo("/SearchByReports.fxml", reportsSearchButton);
    }

    private void navigateTo(String fxmlPath, Button sourceButton) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Stage stage = (Stage) sourceButton.getScene().getWindow();
            // Сохраняем текущие размеры окна
            stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Не удалось открыть экран: " + fxmlPath);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ДЕТАЛИ СТАТЬИ
    // ═══════════════════════════════════════════════════════════

    private void showArticleDetails(Article article) {
        if (article == null) { clearFields(); return; }

        articleTitleField.setText(nvl(article.getTitle()));
        articleLinkField.setText(nvl(article.getLink()));
        newsRoomField.setText(nvl(article.getPublisher()));
        languageField.setText(nvl(article.getArticleLanguage()));
        regionField.setText(nvl(article.getArticleRegion()));

        if (article.getPublicationDate() != null) {
            try {
                LocalDate d;
                // java.sql.Date.toInstant() бросает UnsupportedOperationException — обходим
                if (article.getPublicationDate() instanceof java.sql.Date sqlDate) {
                    d = sqlDate.toLocalDate();
                } else {
                    d = article.getPublicationDate().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate();
                }
                publicationDateField.setText(d.toString());
            } catch (Exception e) {
                publicationDateField.setText(article.getPublicationDate().toString());
            }
        } else {
            publicationDateField.setText("");
        }

        // Теги
        try {
            tagsListView.setItems(FXCollections.observableArrayList(
                    tagPercentService.getArticleTags(article)));
        } catch (Exception e) {
            System.err.println("❌ Теги: " + e.getMessage());
        }

        // Оценки + Blocks (из одного запроса)
        try {
            List<Report> reports = reportService.getReportsByArticle(article);

            // Grade список — только оценки (+1, 0, -1), без блокировок
            List<Report> grades = reports.stream()
                    .filter(r -> r.getRating() != -2)
                    .collect(java.util.stream.Collectors.toList());
            gradeListView.setItems(FXCollections.observableArrayList(grades));

            // Диаграмма — все реакции включая блокировки (полная картина)
            updateGradePieChart(reports);

            // Blocks — только пользователи с rating == -2
            List<User> blockedBy = reports.stream()
                    .filter(r -> r.getRating() == -2)
                    .map(Report::getUser)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
            blocksListView.setItems(FXCollections.observableArrayList(blockedBy));
        } catch (Exception e) {
            System.err.println("❌ Репорты/блокировки: " + e.getMessage());
        }
    }

    // Единая палитра реакций
    private static final String COLOR_LIKE    = "#2E7D32"; // зелёный  — Liked
    private static final String COLOR_READ    = "#D4AC0D"; // жёлтый   — Read
    private static final String COLOR_DISLIKE = "#C0392B"; // красный  — Disliked
    private static final String COLOR_BLOCK   = "#6E1010"; // тёмно-красный — Blocked
    private static final String COLOR_EMPTY   = "#AAAAAA"; // серый    — нет оценок

    private void updateGradePieChart(List<Report> reports) {
        long liked    = reports.stream().filter(r -> r.getRating() ==  1).count();
        long read     = reports.stream().filter(r -> r.getRating() ==  0).count();
        long disliked = reports.stream().filter(r -> r.getRating() == -1).count();
        long blocked  = reports.stream().filter(r -> r.getRating() == -2).count();

        gradePieChart.getData().clear();

        if (liked == 0 && read == 0 && disliked == 0 && blocked == 0) {
            addSlice("No reactions", 1, COLOR_EMPTY);
            return;
        }

        if (liked    > 0) addSlice("Liked",    liked,    COLOR_LIKE);
        if (read     > 0) addSlice("Read",     read,     COLOR_READ);
        if (disliked > 0) addSlice("Disliked", disliked, COLOR_DISLIKE);
        if (blocked  > 0) addSlice("Blocked",  blocked,  COLOR_BLOCK);
    }

    /** Добавляет срез и применяет цвет как только JavaFX создаст ноду (надёжно для PieChart). */
    private void addSlice(String name, long value, String color) {
        PieChart.Data slice = new PieChart.Data(name, value);
        gradePieChart.getData().add(slice);
        // Нода создаётся асинхронно — слушаем её появление
        slice.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) newNode.setStyle("-fx-pie-color: " + color + ";");
        });
        // На случай если нода уже существует в момент добавления
        if (slice.getNode() != null) {
            slice.getNode().setStyle("-fx-pie-color: " + color + ";");
        }
    }

    private void clearFields() {
        articleTitleField.clear();
        articleLinkField.clear();
        newsRoomField.clear();
        languageField.clear();
        regionField.clear();
        publicationDateField.clear();
        tagsListView.setItems(FXCollections.observableArrayList());
        gradeListView.setItems(FXCollections.observableArrayList());
        blocksListView.setItems(FXCollections.observableArrayList());
        gradePieChart.getData().clear();
    }

    // ═══════════════════════════════════════════════════════════
    // СОХРАНЕНИЕ / УДАЛЕНИЕ
    // ═══════════════════════════════════════════════════════════

    @FXML
    private void onSave(ActionEvent event) {
        if (selectedArticle == null) {
            showAlert(Alert.AlertType.WARNING, "Выберите статью для сохранения!");
            return;
        }
        try {
            selectedArticle.setTitle(articleTitleField.getText());
            selectedArticle.setLink(articleLinkField.getText());
            selectedArticle.setPublisher(newsRoomField.getText());
            selectedArticle.setArticleLanguage(languageField.getText());
            selectedArticle.setArticleRegion(regionField.getText());

            boolean ok = articleService.updateArticle(selectedArticle);
            showAlert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR,
                    ok ? "✅ Статья сохранена!" : "❌ Ошибка сохранения!");
            if (ok) articlesListView.refresh();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "❌ " + e.getMessage());
        }
    }

    @FXML
    private void onDelete(ActionEvent event) {
        if (selectedArticle == null) {
            showAlert(Alert.AlertType.WARNING, "Выберите статью для удаления!");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить \"" + selectedArticle.getTitle() + "\"?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            boolean ok = articleService.deleteArticle(selectedArticle.getId());
            if (ok) {
                articlesListView.getItems().remove(selectedArticle);
                clearFields();
                selectedArticle = null;
                showAlert(Alert.AlertType.INFORMATION, "✅ Статья удалена!");
            } else {
                showAlert(Alert.AlertType.ERROR, "❌ Ошибка удаления!");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // COPY КНОПКИ
    // ═══════════════════════════════════════════════════════════

    @FXML private void copyArticleName() { copyToClipboard(articleTitleField.getText()); }
    @FXML private void copyLink()        { copyToClipboard(articleLinkField.getText()); }
    @FXML private void copyNewsRoom()    { copyToClipboard(newsRoomField.getText()); }

    // ═══════════════════════════════════════════════════════════
    // УТИЛИТЫ
    // ═══════════════════════════════════════════════════════════

    private void copyToClipboard(String text) {
        if (text == null || text.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Нечего копировать!"); return;
        }
        Clipboard cb = Clipboard.getSystemClipboard();
        ClipboardContent cc = new ClipboardContent();
        cc.putString(text);
        cb.setContent(cc);
    }

    private void showAlert(Alert.AlertType type, String msg) {
        Alert a = new Alert(type);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private String nvl(String s) { return s != null ? s : ""; }
}
