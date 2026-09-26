package org.example.email;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.FlagTerm;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Читает непрочитанные письма из Gmail через IMAP.
 */
public class EmailReader {

    private final String email;       // твой gmail адрес
    private final String appPassword; // App Password из настроек Google

    public EmailReader(String email, String appPassword) {
        this.email = email;
        this.appPassword = appPassword;
    }

    /**
     * Подключается к Gmail и для каждого непрочитанного письма:
     * 1. Читает письмо
     * 2. Вызывает processor(email) — AI анализ + сохранение в БД
     * 3. Помечает письмо как прочитанное ТОЛЬКО после обработки
     *
     * @return количество обработанных писем
     */
    public int readAndProcess(Consumer<EmailMessage> processor) {
        Properties props = new Properties();
        props.put("mail.imap.host", "imap.gmail.com");
        props.put("mail.imap.port", "993");
        props.put("mail.imap.ssl.enable", "true");

        int processed = 0;

        try {
            Session session = Session.getInstance(props);
            Store store = session.getStore("imap");
            store.connect("imap.gmail.com", email, appPassword);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            Message[] messages = inbox.search(
                    new FlagTerm(new Flags(Flags.Flag.SEEN), false)
            );

            System.out.println("Найдено непрочитанных писем: " + messages.length);

            for (Message message : messages) {
                try {
                    String subject = message.getSubject();
                    String from    = message.getFrom()[0].toString();
                    String body    = extractText(message);
                    String html    = extractHtml(message);

                    EmailMessage emailMessage = new EmailMessage(subject, body, from, message.getReceivedDate());
                    emailMessage.setHtmlBody(html);

                    System.out.println("\nПрочитано: " + emailMessage);

                    // Обрабатываем письмо (AI + сохранение) — ДО пометки как прочитанное
                    processor.accept(emailMessage);

                    // Помечаем как прочитанное только после успешной обработки
                    message.setFlag(Flags.Flag.SEEN, true);
                    processed++;

                } catch (Exception e) {
                    System.err.println("Ошибка при обработке письма: " + e.getMessage());
                }
            }

            inbox.close(true);
            store.close();

        } catch (Exception e) {
            System.err.println("Ошибка подключения к Gmail: " + e.getMessage());
            e.printStackTrace();
            return -1; // отличаем ошибку соединения от "нет писем"
        }

        return processed;
    }

    @Deprecated
    public List<EmailMessage> readUnreadEmails() {
        List<EmailMessage> result = new ArrayList<>();
        readAndProcess(result::add);
        return result;
    }

    /**
     * Извлекает текст из письма (поддерживает plain text и HTML).
     */
    private String extractText(Message message) throws Exception {
        Object content = message.getContent();

        // Простое текстовое письмо
        if (content instanceof String) {
            return (String) content;
        }

        // Multipart письмо (HTML + plain text + вложения)
        if (content instanceof MimeMultipart multipart) {
            return extractFromMultipart(multipart);
        }

        return "";
    }

    private String extractFromMultipart(MimeMultipart multipart) throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            String contentType = part.getContentType().toLowerCase();
            if (contentType.contains("text/plain")) {
                text.append(part.getContent().toString());
            } else if (contentType.contains("multipart")) {
                text.append(extractFromMultipart((MimeMultipart) part.getContent()));
            }
        }
        return text.toString();
    }

    /**
     * Извлекает HTML-тело письма для последующего парсинга скрытых ссылок.
     */
    private String extractHtml(Message message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String s) {
            String ct = message.getContentType().toLowerCase();
            return ct.contains("text/html") ? s : null;
        }
        if (content instanceof MimeMultipart multipart) {
            return extractHtmlFromMultipart(multipart);
        }
        return null;
    }

    private String extractHtmlFromMultipart(MimeMultipart multipart) throws Exception {
        String result = null;
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            String contentType = part.getContentType().toLowerCase();
            if (contentType.contains("text/html")) {
                return part.getContent().toString();
            } else if (contentType.contains("multipart")) {
                result = extractHtmlFromMultipart((MimeMultipart) part.getContent());
            }
        }
        return result;
    }
}