package org.example.controller;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.NotificationFrequency;
import org.example.Report;
import org.example.service.ReportService;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.ResourceBundle;

public class SearchByReportsController implements Initializable {

    @FXML private Button keywordSearchButton;
    @FXML private Button usernameSearchButton;
    @FXML private Button reportsSearchButton;
    @FXML private Button settingsButton;
    @FXML private Button logoutButton;
    @FXML private VBox   settingsOverlay;
    @FXML private VBox   logoutOverlay;
    @FXML private Button notifFreqButton;

    @FXML private TextField keywordSearchField;
    @FXML private DatePicker searchDatePicker;

    @FXML private ListView<Report> reportsListView;

    @FXML private TextField reportDateField;
    @FXML private TextField appliedUserField;
    @FXML private TextField blockedArticleTitleField;
    @FXML private TextField blockedArticleLinkField;
    @FXML private TextArea  reasonField;

    private final ReportService reportService = new ReportService();
    private Report selectedReport;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        System.out.println("SearchByReportsController init");
        setupReportsListView();
        loadAllReports();
    }

    private void setupReportsListView() {
        reportsListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Report item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                String label = "Report " + item.getId();
                if (item.getUser() != null) label += "  -  " + item.getUser().getNick();
                setText(label);
                setStyle(item.equals(selectedReport)
                        ? "-fx-text-fill: #922B21; -fx-font-weight: bold;"
                        : "-fx-text-fill: #294946; -fx-font-weight: bold;");
            }
        });

        reportsListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    selectedReport = newVal;
                    reportsListView.refresh();
                    showReportDetails(newVal);
                }
        );
    }

    private void loadAllReports() {
        try {
            List<Report> reports = reportService.getReportsByRating(-2);
            reportsListView.setItems(FXCollections.observableArrayList(reports));
            System.out.println("Loaded blocks: " + reports.size());
        } catch (Exception e) {
            System.err.println("Error loading blocks: " + e.getMessage());
        }
    }

    @FXML
    private void onSearch(ActionEvent event) {
        String keyword = keywordSearchField.getText().trim();
        LocalDate date = searchDatePicker.getValue();
        if (keyword.isEmpty() && date == null) { loadAllReports(); return; }
        try {
            List<Report> filtered = reportService.getReportsByRating(-2).stream()
                    .filter(r -> keyword.isEmpty() ||
                            (r.getUser() != null && r.getUser().getNick().toLowerCase().contains(keyword.toLowerCase())) ||
                            (r.getReason() != null && r.getReason().toLowerCase().contains(keyword.toLowerCase())) ||
                            (r.getArticle() != null && r.getArticle().getTitle() != null && r.getArticle().getTitle().toLowerCase().contains(keyword.toLowerCase())))
                    .filter(r -> date == null || date.equals(toLocalDate(r.getDate())))
                    .toList();
            reportsListView.setItems(FXCollections.observableArrayList(filtered));
            if (filtered.isEmpty()) showAlert(Alert.AlertType.INFORMATION, "Reports not found");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Search error: " + e.getMessage());
        }
    }

    private void showReportDetails(Report report) {
        if (report == null) { clearFields(); return; }

        // ИСПРАВЛЕНИЕ: java.sql.Date не поддерживает toInstant() — используем toLocalDate()
        LocalDate d = toLocalDate(report.getDate());
        reportDateField.setText(d != null ? d.toString() : "");

        appliedUserField.setText(report.getUser() != null ? report.getUser().getNick() : "");
        blockedArticleTitleField.setText(
                report.getArticle() != null && report.getArticle().getTitle() != null
                        ? report.getArticle().getTitle() : "");
        blockedArticleLinkField.setText(
                report.getArticle() != null && report.getArticle().getLink() != null
                        ? report.getArticle().getLink() : "");
        reasonField.setText(report.getReason() != null ? report.getReason() : "");
    }

    /**
     * Безопасное преобразование Date в LocalDate.
     * java.sql.Date.toInstant() бросает UnsupportedOperationException!
     */
    private LocalDate toLocalDate(java.util.Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private void clearFields() {
        reportDateField.clear();
        appliedUserField.clear();
        blockedArticleTitleField.clear();
        blockedArticleLinkField.clear();
        reasonField.clear();
    }

    @FXML private void copyReportDate()   { copyToClipboard(reportDateField.getText()); }
    @FXML private void copyAppliedUser()  { copyToClipboard(appliedUserField.getText()); }
    @FXML private void copyBlockedTitle() { copyToClipboard(blockedArticleTitleField.getText()); }
    @FXML private void copyBlockedLink()  { copyToClipboard(blockedArticleLinkField.getText()); }
    @FXML private void copyReason()       { copyToClipboard(reasonField.getText()); }

    @FXML private void onSettings(ActionEvent event) {
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

    @FXML private void onLogout(ActionEvent event) {
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

    @FXML private void onKeywordSearch(ActionEvent event) { navigateTo("/SearchByKeyword.fxml", keywordSearchButton); }
    @FXML private void onUsernameSearch(ActionEvent event) { navigateTo("/SearchByUsername.fxml", usernameSearchButton); }
    @FXML private void onReportsSearch(ActionEvent event) { System.out.println("Already on Reports page"); }

    private void navigateTo(String fxmlPath, Button sourceButton) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Stage stage = (Stage) sourceButton.getScene().getWindow();
            stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Cannot open: " + fxmlPath);
        }
    }

    private void copyToClipboard(String text) {
        if (text == null || text.isBlank()) { showAlert(Alert.AlertType.WARNING, "Nothing to copy!"); return; }
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
}