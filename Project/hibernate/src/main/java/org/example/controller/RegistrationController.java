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

public class RegistrationController {

    @FXML private TextField     loginField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label         errorLabel;
    @FXML private Button        backButton;
    @FXML private Button        enterButton;

    private final UserService userService = new UserService();

    @FXML
    private void onEnter(ActionEvent event) {
        errorLabel.setText("");

        String nick    = loginField.getText().trim();
        String pass    = passwordField.getText();
        String confirm = confirmPasswordField.getText();

        // Validation
        StringBuilder errors = new StringBuilder();

        if (nick.isEmpty()) {
            errors.append("• Login cannot be empty\n");
        } else if (nick.length() < 3) {
            errors.append("• Login must be at least 3 characters\n");
        } else if (userService.getUserByNick(nick).isPresent()) {
            errors.append("• Login is already taken\n");
        }

        if (pass.isEmpty()) {
            errors.append("• Password cannot be empty\n");
        } else {
            if (pass.length() < 8) {
                errors.append("• Password must be at least 8 characters\n");
            }
            if (!pass.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
                errors.append("• Password must contain at least 1 special character\n");
            }
        }

        if (!pass.equals(confirm)) {
            errors.append("• Passwords do not match\n");
        }

        if (errors.length() > 0) {
            errorLabel.setText(errors.toString().trim());
            return;
        }

        try {
            User user = userService.registerUserNoEmail(nick, pass);
            navigateToLanguageRegion(user);
        } catch (IllegalArgumentException e) {
            errorLabel.setText("• " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            errorLabel.setText("• Registration error: " + e.getMessage());
        }
    }

    @FXML
    private void onBackToLogin(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(new Scene(root, 430, 932));
            stage.setResizable(false);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            errorLabel.setText("• Navigation error");
        }
    }

    private void navigateToLanguageRegion(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/LanguageRegion.fxml"));
            Parent root = loader.load();
            LanguageRegionController controller = loader.getController();
            controller.setCurrentUser(user);
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
