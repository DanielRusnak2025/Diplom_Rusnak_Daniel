package org.example.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;
import org.example.Tag;
import org.example.User;
import org.example.service.TagPercentService;
import org.example.service.TagService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CategoriesController {

    @FXML private FlowPane categoriesPane;
    @FXML private Label    errorLabel;
    @FXML private Button   enterButton;

    private final TagService        tagService        = new TagService();
    private final TagPercentService tagPercentService = new TagPercentService();

    private User currentUser;

    private static final String STYLE_UNSELECTED =
            "-fx-background-color: #EDE0C1; -fx-text-fill: #294946; -fx-border-color: #294946;" +
            "-fx-background-radius: 45; -fx-border-radius: 45; -fx-border-width: 2;" +
            "-fx-cursor: hand; -fx-padding: 7 14 7 14; -fx-font-size: 13;";
    private static final String STYLE_SELECTED =
            "-fx-background-color: #294946; -fx-text-fill: #D4C398; -fx-border-color: #294946;" +
            "-fx-background-radius: 45; -fx-border-radius: 45; -fx-border-width: 2;" +
            "-fx-cursor: hand; -fx-padding: 7 14 7 14; -fx-font-size: 13;";

    @FXML
    private void initialize() {
        List<Tag> topTags = tagService.getMostPopularTags(15);
        for (Tag tag : topTags) {
            ToggleButton tb = new ToggleButton(formatTagName(tag.getName()));
            tb.setUserData(tag.getName()); // raw DB name
            tb.setStyle(STYLE_UNSELECTED);
            tb.setOnAction(this::onToggle);
            categoriesPane.getChildren().add(tb);
        }
    }

    /** "artificial_intelligence" → "Artificial Intelligence" */
    private String formatTagName(String rawName) {
        String[] parts = rawName.split("[_\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    @FXML
    private void onToggle(ActionEvent event) {
        ToggleButton tb = (ToggleButton) event.getSource();
        tb.setStyle(tb.isSelected() ? STYLE_SELECTED : STYLE_UNSELECTED);
    }

    @FXML
    private void onEnter(ActionEvent event) {
        errorLabel.setText("");
        List<String> selectedTagNames = new ArrayList<>();
        for (Node node : categoriesPane.getChildren()) {
            if (node instanceof ToggleButton tb && tb.isSelected()) {
                selectedTagNames.add((String) tb.getUserData());
            }
        }

        if (selectedTagNames.size() < 3) {
            errorLabel.setText("• Please select at least 3 categories");
            return;
        }

        try {
            for (String tagName : selectedTagNames) {
                Tag tag = tagService.getOrCreateTag(tagName);
                tagPercentService.setUserTagPercent(currentUser, tag, 60);
            }
            navigateToMain();
        } catch (Exception e) {
            e.printStackTrace();
            errorLabel.setText("• Error saving categories: " + e.getMessage());
        }
    }

    private void navigateToMain() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/MainScreen.fxml"));
            Parent root = loader.load();
            MainScreenController controller = loader.getController();
            controller.setCurrentUser(currentUser);
            Stage stage = (Stage) enterButton.getScene().getWindow();
            stage.setScene(new Scene(root, 520, 932));
            stage.setResizable(false);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
