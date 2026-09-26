package org.example.controller;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
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
 * Контроллер для SearchByUsername.fxml
 * Отвечает за: поиск пользователей, отображение и редактирование данных пользователя
 */
public class SearchByUsernameController implements Initializable {

    // ═══════════════════════════════════════════════════════════
    // ПОИСКОВАЯ ПАНЕЛЬ
    // ═══════════════════════════════════════════════════════════
    @FXML private TextField keywordSearchField;
    @FXML private DatePicker searchDatePicker;
    @FXML private Button keywordSearchButton;
    @FXML private Button usernameSearchButton;
    @FXML private Button reportsSearchButton;

    // ═══════════════════════════════════════════════════════════
    // СПИСОК ПОЛЬЗОВАТЕЛЕЙ
    // ═══════════════════════════════════════════════════════════
    @FXML private ListView<User> usersListView;
    @FXML private ListView<Report> blocksListView;
    @FXML private ListView<UserTagPercent> tagsListView;
    @FXML private ListView<Report> gradeListView;

    // ═══════════════════════════════════════════════════════════
    // ПОЛЯ ПОЛЬЗОВАТЕЛЯ
    // ═══════════════════════════════════════════════════════════
    @FXML private TextField userNameField;
    @FXML private TextField currentPasswordField;
    @FXML private TextField firstPasswordField;
    @FXML private TextField registrationDateField;
    @FXML private TextField gmailField;
    @FXML private TextField isAdminField;
    @FXML private ComboBox<String> preferredRegionCombo;
    @FXML private ComboBox<String> preferredLanguageCombo;

    // ═══════════════════════════════════════════════════════════
    // ДИАГРАММА И КНОПКИ
    // ═══════════════════════════════════════════════════════════
    @FXML private PieChart gradePieChart;
    @FXML private Button settingsButton;
    @FXML private Button logoutButton;
    @FXML private VBox   settingsOverlay;
    @FXML private VBox   logoutOverlay;
    @FXML private Button notifFreqButton;
    @FXML private Button saveButton;
    @FXML private Button deleteButton;

    // ═══════════════════════════════════════════════════════════
    // СЕРВИСЫ
    // ═══════════════════════════════════════════════════════════
    private final UserService userService             = new UserService();
    private final TagPercentService tagPercentService = new TagPercentService();
    private final ReportService reportService         = new ReportService();

    private User selectedUser;

    // ═══════════════════════════════════════════════════════════
    // ИНИЦИАЛИЗАЦИЯ
    // ═══════════════════════════════════════════════════════════
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        System.out.println("🚀 SearchByUsernameController инициализирован");

        setupUsersListView();
        setupTagsListView();
        setupGradeListView();
        setupBlocksListView();

        loadAllUsers();
    }

    // ═══════════════════════════════════════════════════════════
    // НАСТРОЙКА ОТОБРАЖЕНИЯ СПИСКОВ
    // ═══════════════════════════════════════════════════════════

    private void setupUsersListView() {
        usersListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item.getNick());
                // Если выбранный — выделить красным (как на макете)
                if (item.equals(selectedUser)) {
                    setStyle("-fx-text-fill: #922b21; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #294946; -fx-font-weight: bold;");
                }
            }
        });

        // Слушатель выбора пользователя
        usersListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    selectedUser = newVal;
                    usersListView.refresh(); // обновить цвет строк
                    showUserDetails(newVal);
                }
        );
    }

    private void setupTagsListView() {
        tagsListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(UserTagPercent item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }

                int pct = item.getPercent();
                String sign = pct >= 0 ? "+" : "";
                String color = pct >= 0 ? "#294946" : "#922b21";

                // Название тега и процент
                setText(null);
                javafx.scene.layout.HBox box = new javafx.scene.layout.HBox();
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                Label name = new Label(item.getTag().getName());
                name.setMaxWidth(Double.MAX_VALUE);
                name.setStyle("-fx-font-family:'Arial Bold';-fx-font-size:14;-fx-text-fill:#294946;");
                javafx.scene.layout.HBox.setHgrow(name, javafx.scene.layout.Priority.ALWAYS);

                Label percent = new Label(sign + pct + "%");
                percent.setStyle("-fx-font-family:'Arial Bold';-fx-font-size:14;-fx-text-fill:" + color + ";");

                box.getChildren().addAll(name, percent);
                setGraphic(box);
            }
        });
    }

    private void setupGradeListView() {
        gradeListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Report item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }

                String ratingIcon = switch (item.getRating()) {
                    case 1  -> "👍";
                    case 0  -> "✓";
                    case -1 -> "👎";
                    case -2 -> "⚠";
                    default -> "?";
                };
                String ratingLabel = switch (item.getRating()) {
                    case 1  -> "Like";
                    case 0  -> "Neutral";
                    case -1 -> "Dislike";
                    case -2 -> "Report";
                    default -> "Unknown";
                };
                String title = item.getArticle() != null
                        ? item.getArticle().getTitle()
                        : "(article deleted)";
                // Обрезаем длинные заголовки
                if (title != null && title.length() > 55) title = title.substring(0, 52) + "…";

                javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(8);
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                Label icon = new Label(ratingIcon + " " + ratingLabel);
                icon.setPrefWidth(85);
                icon.setStyle("-fx-font-size:12; -fx-text-fill:#294946; -fx-font-weight:bold;");

                Label articleTitle = new Label(title);
                articleTitle.setStyle("-fx-font-size:12; -fx-text-fill:#555;");

                box.getChildren().addAll(icon, articleTitle);
                setGraphic(box);
                setText(null);
            }
        });
    }

    private void setupBlocksListView() {
        blocksListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Report item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }

                String title = item.getArticle() != null
                        ? item.getArticle().getTitle()
                        : "(article deleted)";
                if (title != null && title.length() > 50) title = title.substring(0, 47) + "…";

                String reason = item.getReason() != null && !item.getReason().isBlank()
                        ? item.getReason()
                        : "—";

                javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(8);
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                Label titleLabel = new Label(title);
                titleLabel.setPrefWidth(200);
                titleLabel.setStyle("-fx-font-size:12; -fx-text-fill:#922b21; -fx-font-weight:bold;");

                Label reasonLabel = new Label(reason);
                reasonLabel.setStyle("-fx-font-size:11; -fx-text-fill:#555;");

                box.getChildren().addAll(titleLabel, reasonLabel);
                setGraphic(box);
                setText(null);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    // ЗАГРУЗКА ПОЛЬЗОВАТЕЛЕЙ
    // ═══════════════════════════════════════════════════════════

    private void loadAllUsers() {
        try {
            List<User> users = userService.getAllUsers();
            usersListView.setItems(FXCollections.observableArrayList(users));
            System.out.println("✅ Загружено пользователей: " + users.size());
        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки пользователей: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ПОИСК ПОЛЬЗОВАТЕЛЕЙ
    // ═══════════════════════════════════════════════════════════

    @FXML
    private void onUsernameSearch(ActionEvent event) {
        String keyword = keywordSearchField.getText().trim();
        LocalDate date  = searchDatePicker.getValue();

        // Если поля пустые — показываем всех
        if (keyword.isEmpty() && date == null) {
            loadAllUsers();
            return;
        }

        try {
            List<User> all = userService.getAllUsers();

            List<User> filtered = all.stream()
                    .filter(u -> keyword.isEmpty() ||
                            u.getNick().toLowerCase().contains(keyword.toLowerCase()))
                    .filter(u -> {
                        if (date == null || u.getRegistrationDate() == null) return true;
                        LocalDate regDate = u.getRegistrationDate().toInstant()
                                .atZone(ZoneId.systemDefault()).toLocalDate();
                        return regDate.equals(date);
                    })
                    .toList();

            usersListView.setItems(FXCollections.observableArrayList(filtered));

            if (filtered.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "Пользователи не найдены");
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
     * Переход на SearchByKeyword.fxml (поиск статей)
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
            MenuItem item = new MenuItem(freq.getValue());
            item.setOnAction(ev -> notifFreqButton.setText(freq.getValue() + " ▶"));
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
    private void onKeywordSearch(ActionEvent event) {
        navigateTo("/SearchByKeyword.fxml", keywordSearchButton);
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
            stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Не удалось открыть экран: " + fxmlPath);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ОТОБРАЖЕНИЕ ДАННЫХ ПОЛЬЗОВАТЕЛЯ
    // ═══════════════════════════════════════════════════════════

    private void showUserDetails(User user) {
        if (user == null) { clearFields(); return; }

        System.out.println("👤 Выбран пользователь: " + user.getNick());

        userNameField.setText(nvl(user.getNick()));
        currentPasswordField.setText(nvl(user.getPassword()));
        firstPasswordField.setText(nvl(user.getFirstPassword()));
        gmailField.setText(nvl(user.getEmail()));
        isAdminField.setText(user.isAdmin() ? "Yes" : "No");

        // Дата регистрации
        if (user.getRegistrationDate() != null) {
            try {
                LocalDate d;
                if (user.getRegistrationDate() instanceof java.sql.Date sqlDate) {
                    d = sqlDate.toLocalDate();
                } else {
                    d = user.getRegistrationDate().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate();
                }
                registrationDateField.setText(d.toString());
            } catch (Exception ex) {
                registrationDateField.setText(user.getRegistrationDate().toString());
            }
        } else {
            registrationDateField.setText("");
        }

        // ComboBox — регион и язык
        if (preferredRegionCombo != null) {
            preferredRegionCombo.setValue(user.getPreferredRegion());
        }
        if (preferredLanguageCombo != null) {
            preferredLanguageCombo.setValue(user.getPreferredLanguage());
        }

        // Теги пользователя
        try {
            List<UserTagPercent> tags = tagPercentService.getUserTags(user);
            tagsListView.setItems(FXCollections.observableArrayList(tags));
        } catch (Exception e) {
            System.err.println("❌ Теги пользователя: " + e.getMessage());
        }

        // Отчёты пользователя (Grade)
        try {
            List<Report> reports = reportService.getReportsByUser(user);
            gradeListView.setItems(FXCollections.observableArrayList(reports));
            updatePieChart(reports);
        } catch (Exception e) {
            System.err.println("❌ Отчёты пользователя: " + e.getMessage());
        }

        // Blocks — отчёты с низким рейтингом (-2)
        try {
            List<Report> blocks = reportService.getReportsByUser(user).stream()
                    .filter(r -> r.getRating() <= -1)
                    .toList();
            blocksListView.setItems(FXCollections.observableArrayList(blocks));
        } catch (Exception e) {
            System.err.println("❌ Blocks: " + e.getMessage());
        }
    }

    /**
     * Заполняет круговую диаграмму Grade данными отчётов пользователя.
     * Rad(1), Liked(0), Didn't Liked(-1), Blocked(-2)
     */
    private void updatePieChart(List<Report> reports) {
        if (gradePieChart == null || reports == null) return;

        long rad     = reports.stream().filter(r -> r.getRating() == 1).count();
        long liked   = reports.stream().filter(r -> r.getRating() == 0).count();
        long disliked= reports.stream().filter(r -> r.getRating() == -1).count();
        long blocked = reports.stream().filter(r -> r.getRating() == -2).count();

        gradePieChart.getData().clear();

        if (rad > 0)      gradePieChart.getData().add(new PieChart.Data("Rad",          rad));
        if (liked > 0)    gradePieChart.getData().add(new PieChart.Data("Liked",        liked));
        if (disliked > 0) gradePieChart.getData().add(new PieChart.Data("Didn't Liked", disliked));
        if (blocked > 0)  gradePieChart.getData().add(new PieChart.Data("Blocked",      blocked));

        java.util.Map<String, String> pieColors = java.util.Map.of(
                "Rad",          "#4CAF50",
                "Liked",        "#FFC107",
                "Didn't Liked", "#FF7043",
                "Blocked",      "#C0392B"
        );
        for (PieChart.Data d : gradePieChart.getData()) {
            String color = pieColors.getOrDefault(d.getName(), "#888888");
            d.getNode().setStyle("-fx-pie-color: " + color + ";");
        }
    }

    private void clearFields() {
        userNameField.clear();
        currentPasswordField.clear();
        firstPasswordField.clear();
        registrationDateField.clear();
        gmailField.clear();
        isAdminField.setText("No");
        if (preferredRegionCombo != null)   preferredRegionCombo.setValue(null);
        if (preferredLanguageCombo != null) preferredLanguageCombo.setValue(null);
        tagsListView.setItems(FXCollections.observableArrayList());
        gradeListView.setItems(FXCollections.observableArrayList());
        blocksListView.setItems(FXCollections.observableArrayList());
        if (gradePieChart != null) gradePieChart.getData().clear();
    }

    // ═══════════════════════════════════════════════════════════
    // СОХРАНЕНИЕ ПОЛЬЗОВАТЕЛЯ
    // ═══════════════════════════════════════════════════════════

    @FXML
    private void onSave(ActionEvent event) {
        if (selectedUser == null) {
            showAlert(Alert.AlertType.WARNING, "Выберите пользователя для сохранения!");
            return;
        }

        try {
            selectedUser.setPassword(currentPasswordField.getText());
            selectedUser.setEmail(gmailField.getText());
            selectedUser.setAdmin(isAdminField.getText().equalsIgnoreCase("Yes"));

            if (preferredRegionCombo != null)
                selectedUser.setPreferredRegion(preferredRegionCombo.getValue());
            if (preferredLanguageCombo != null)
                selectedUser.setPreferredLanguage(preferredLanguageCombo.getValue());

            boolean ok = userService.updateUser(selectedUser);
            showAlert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR,
                    ok ? "✅ Пользователь сохранён!" : "❌ Ошибка сохранения!");
            if (ok) usersListView.refresh();

        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "❌ " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // УДАЛЕНИЕ ПОЛЬЗОВАТЕЛЯ
    // ═══════════════════════════════════════════════════════════

    @FXML
    private void onDeleteUser(ActionEvent event) {
        if (selectedUser == null) {
            showAlert(Alert.AlertType.WARNING, "Выберите пользователя для удаления!");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить пользователя \"" + selectedUser.getNick() + "\"?\n" +
                "Это удалит все его данные, теги и отчёты.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                userService.deleteUser(selectedUser.getNick());
                usersListView.getItems().remove(selectedUser);
                clearFields();
                selectedUser = null;
                showAlert(Alert.AlertType.INFORMATION, "✅ Пользователь удалён!");
            } catch (Exception e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "❌ Ошибка удаления: " + e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // TOGGLE Is Admin
    // ═══════════════════════════════════════════════════════════

    @FXML
    private void toggleAdmin(ActionEvent event) {
        if (isAdminField == null) return;
        boolean current = isAdminField.getText().equalsIgnoreCase("Yes");
        isAdminField.setText(current ? "No" : "Yes");
    }

    // ═══════════════════════════════════════════════════════════
    // COPY КНОПКИ
    // ═══════════════════════════════════════════════════════════

    @FXML private void copyUserName()  { copyToClipboard(userNameField.getText()); }
    @FXML private void copyGmail()     { copyToClipboard(gmailField.getText()); }
    @FXML private void editCurrentPassword() { currentPasswordField.setEditable(true); currentPasswordField.requestFocus(); }
    @FXML private void editFirstPassword()   { firstPasswordField.setEditable(true);   firstPasswordField.requestFocus(); }
    @FXML private void editGmail()           { gmailField.setEditable(true);            gmailField.requestFocus(); }

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
