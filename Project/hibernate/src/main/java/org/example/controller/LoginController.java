package org.example.controller;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.example.Article;
import org.example.ArticleTagPercent;
import org.example.User;
import org.example.service.ArticleService;
import org.example.service.UserService;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    @FXML private ListView<Article> articlesCarousel;
    @FXML private TextField loginField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button enterButton;

    private final ArticleService articleService = new ArticleService();
    private final UserService    userService    = new UserService();
    private static final int CAROUSEL_SIZE = 10;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupCarousel();
        loadArticles();
    }

    // ═══════════════════════════════════════════
    // КАРУСЕЛЬ
    // ═══════════════════════════════════════════

    private void setupCarousel() {
        articlesCarousel.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Article article, boolean empty) {
                super.updateItem(article, empty);
                if (empty || article == null) {
                    setGraphic(null);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }
                setGraphic(buildCard(article));
                setText(null);
                setStyle("-fx-background-color: transparent; -fx-padding: 5 0 5 0;");
            }
        });

        articlesCarousel.setOnMouseClicked(e -> {
            Article a = articlesCarousel.getSelectionModel().getSelectedItem();
            if (a != null && a.getLink() != null && !a.getLink().isBlank()) {
                try {
                    if (java.awt.Desktop.isDesktopSupported())
                        java.awt.Desktop.getDesktop().browse(new URI(a.getLink()));
                } catch (Exception ex) {
                    System.err.println("Browser error: " + ex.getMessage());
                }
            }
        });
    }

    private HBox buildCard(Article article) {
        HBox card = new HBox();
        card.setPrefHeight(130);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPadding(new Insets(12, 12, 12, 16));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(
                "-fx-background-color: rgba(0,0,0,0.55);" +
                        "-fx-background-radius: 20;" +
                        "-fx-border-radius: 20;" +
                        "-fx-border-color: #294946;" +
                        "-fx-border-width: 3;" +
                        "-fx-cursor: hand;"
        );

        // Текстовая часть
        VBox textBox = new VBox(4);
        textBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Label titleLbl = new Label(nvl(article.getTitle()));
        titleLbl.setWrapText(true);
        titleLbl.setMaxWidth(310);
        titleLbl.setStyle("-fx-text-fill: #D4C398; -fx-font-size: 14; -fx-font-weight: 800;");

        // Определяем темы по проценту тега
        String main = "", secondary = "";
        if (article.getArticleTagPercents() != null && !article.getArticleTagPercents().isEmpty()) {
            List<ArticleTagPercent> sorted = article.getArticleTagPercents().stream()
                    .filter(t -> t.getTag() != null)
                    .sorted(Comparator.comparingInt(ArticleTagPercent::getPercent).reversed())
                    .toList();
            if (!sorted.isEmpty()) main = sorted.get(0).getTag().getName();
            if (sorted.size() > 1)  secondary = sorted.get(1).getTag().getName();
        }

        Label mainLbl = new Label("Main theme:  " + main);
        mainLbl.setStyle("-fx-text-fill: #D4C398; -fx-font-size: 13; -fx-font-weight: 800;");

        Label secLbl = new Label("Secondary theme:  " + secondary);
        secLbl.setStyle("-fx-text-fill: #D4C398; -fx-font-size: 13; -fx-font-weight: 800;");

        textBox.getChildren().addAll(titleLbl, mainLbl, secLbl);

        StackPane langCircle = buildLangBadge(article.getArticleLanguage());
        HBox.setMargin(langCircle, new Insets(0, 0, 0, 10));

        card.getChildren().addAll(textBox, langCircle);
        return card;
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
        lbl.setStyle("-fx-font-family: 'Inter', 'Arial'; -fx-text-fill: #D4C398; -fx-font-size: 13; -fx-font-weight: 800;");
        pane.getChildren().add(lbl);
        return pane;
    }

    private void loadArticles() {
        try {
            List<Article> recent = articleService.getRecentArticlesWithTags(CAROUSEL_SIZE);
            articlesCarousel.setItems(FXCollections.observableArrayList(recent));
        } catch (Exception e) {
            System.err.println("Error loading articles: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════
    // ВХОД
    // ═══════════════════════════════════════════

    @FXML
    private void onEnter(ActionEvent event) {
        String login    = loginField.getText().trim();
        String password = passwordField.getText();
        errorLabel.setText("");

        if (login.isEmpty() && password.isEmpty()) {
            errorLabel.setText("• No such Login\n• Incorrect Password");
            return;
        }
        if (login.isEmpty()) {
            errorLabel.setText("• No such Login");
            return;
        }
        if (password.isEmpty()) {
            errorLabel.setText("• Incorrect Password");
            return;
        }

        try {
            Optional<User> userOpt = userService.getUserByNick(login);

            if (userOpt.isEmpty()) {
                errorLabel.setText("• Incorrect login or password");
                return;
            }

            User user = userOpt.get();

            if (!user.getPassword().equals(password)) {
                errorLabel.setText("• Incorrect login or password");
                return;
            }

            // Успех — admin → CRUD, обычный → MainScreen
            if (user.isAdmin()) {
                navigateTo("/SearchByKeyword.fxml");
            } else {
                navigateToMain(user);
            }

        } catch (Exception e) {
            e.printStackTrace();
            errorLabel.setText("• Connection error");
        }
    }

    @FXML
    private void onRegister(MouseEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Registration.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) enterButton.getScene().getWindow();
            stage.setScene(new Scene(root, 520, 932));
            stage.setResizable(false);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            errorLabel.setText("• Не удалось открыть регистрацию");
        }
    }



    // ═══════════════════════════════════════════
    // НАВИГАЦИЯ
    // ═══════════════════════════════════════════

    private void navigateTo(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Stage stage = (Stage) enterButton.getScene().getWindow();
            stage.setMinWidth(0);
            stage.setMinHeight(0);
            stage.setMaxWidth(Double.MAX_VALUE);
            stage.setMaxHeight(Double.MAX_VALUE);
            stage.setResizable(true);
            stage.setWidth(1280);
            stage.setHeight(800);
            stage.setScene(new Scene(root, 1280, 800));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            errorLabel.setText("• Не удалось открыть экран");
        }
    }

    /** Переход на MainScreen для обычного пользователя. */
    private void navigateToMain(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/MainScreen.fxml"));
            Parent root = loader.load();
            MainScreenController controller = loader.getController();
            controller.setCurrentUser(user);

            Stage stage = (Stage) enterButton.getScene().getWindow();
            stage.setScene(new Scene(root, 520, 932));
        } catch (IOException e) {
            e.printStackTrace();
            errorLabel.setText("• Не удалось открыть главный экран");
        }
    }

    private String nvl(String s) { return s != null ? s : ""; }
}