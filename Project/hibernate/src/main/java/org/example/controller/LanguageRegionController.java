package org.example.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.example.User;
import org.example.service.UserService;

import java.io.IOException;
import java.util.List;

public class LanguageRegionController {

    @FXML private Button languageButton;
    @FXML private Button regionButton;
    @FXML private Label  errorLabel;
    @FXML private Button enterButton;

    private final UserService userService = new UserService();

    private User currentUser;
    private String selectedLanguage;
    private String selectedRegion;

    private static final List<String> LANGUAGES = List.of(
            "English", "Russian", "Romanian", "French", "German", "Spanish"
    );
    private static final List<String> REGIONS = List.of(
            "USA", "Europe", "UK", "Moldova", "Asia", "World"
    );

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    @FXML
    private void onLanguage(ActionEvent event) {
        ContextMenu menu = new ContextMenu();
        for (String lang : LANGUAGES) {
            MenuItem item = new MenuItem(lang);
            item.setOnAction(ev -> {
                selectedLanguage = lang;
                languageButton.setText(lang + " ▶");
            });
            menu.getItems().add(item);
        }
        menu.show(languageButton, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    @FXML
    private void onRegion(ActionEvent event) {
        ContextMenu menu = new ContextMenu();
        for (String region : REGIONS) {
            MenuItem item = new MenuItem(region);
            item.setOnAction(ev -> {
                selectedRegion = region;
                regionButton.setText(region + " ▶");
            });
            menu.getItems().add(item);
        }
        menu.show(regionButton, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    @FXML
    private void onEnter(ActionEvent event) {
        errorLabel.setText("");

        if (selectedLanguage == null) {
            errorLabel.setText("• Please select a language");
            return;
        }
        if (selectedRegion == null) {
            errorLabel.setText("• Please select a region");
            return;
        }

        try {
            currentUser = userService.updatePreferences(
                    currentUser.getNick(), selectedRegion, selectedLanguage);
            navigateToCategories();
        } catch (Exception e) {
            e.printStackTrace();
            errorLabel.setText("• Error saving preferences: " + e.getMessage());
        }
    }

    private void navigateToCategories() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Categories.fxml"));
            Parent root = loader.load();
            CategoriesController controller = loader.getController();
            controller.setCurrentUser(currentUser);
            Stage stage = (Stage) enterButton.getScene().getWindow();
            stage.setScene(new Scene(root, 430, 932));
            stage.setResizable(false);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            errorLabel.setText("• Navigation error");
        }
    }
}
