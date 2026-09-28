package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.email.EmailConfig;
import org.example.scheduler.EmailScheduler;

import java.io.IOException;

public class App extends Application {

    private EmailScheduler emailScheduler;

    @Override
    public void start(Stage stage) throws IOException {
        boolean schedulerEnabled = EmailConfig.isSchedulerEnabled();
        boolean cleanupEnabled   = EmailConfig.isCleanupEnabled();
        org.hibernate.cfg.Configuration hCfg = new org.hibernate.cfg.Configuration().configure();
        String dbUrl  = hCfg.getProperty("hibernate.connection.url");
        String dbUser = hCfg.getProperty("hibernate.connection.username");
        System.out.println("[Config] scheduler.enabled=" + schedulerEnabled
                + "  scheduler.cleanup.enabled=" + cleanupEnabled
                + "  db.url=" + dbUrl + "  db.user=" + dbUser);

        if (schedulerEnabled) {
            emailScheduler = new EmailScheduler();
            Thread schedulerThread = new Thread(() -> emailScheduler.start());
            schedulerThread.setDaemon(true);
            schedulerThread.start();
        }

        // Открываем экран логина
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/Login.fxml"));
        Scene scene = new Scene(loader.load(), 520, 932);
        stage.setScene(scene);
        stage.setTitle("NewsDove");
        stage.setMinWidth(520);
        stage.setMaxWidth(520);
        stage.setMinHeight(932);
        stage.setMaxHeight(932);
        stage.setResizable(false);
        stage.show();
    }

    @Override
    public void stop() {
        // Останавливаем планировщик при закрытии приложения
        if (emailScheduler != null) {
            emailScheduler.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}