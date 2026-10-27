package org.example.email;

import java.util.Date;

/**
 * DTO — представляет одно письмо из почты.
 * Содержит всё, что нам нужно передать в AI для анализа.
 */
public class EmailMessage {

    private String subject;    // тема письма
    private String body;       // plain text тело
    private String htmlBody;   // HTML тело (для извлечения скрытых ссылок)
    private String from;       // от кого (например: news@bbc.com)
    private Date receivedDate; // когда получено

    public EmailMessage(String subject, String body, String from, Date receivedDate) {
        this.subject = subject;
        this.body = body;
        this.from = from;
        this.receivedDate = receivedDate;
    }

    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getHtmlBody() { return htmlBody; }
    public void setHtmlBody(String htmlBody) { this.htmlBody = htmlBody; }
    public String getFrom() { return from; }
    public Date getReceivedDate() { return receivedDate; }

    @Override
    public String toString() {
        return "EmailMessage{from='" + from + "', subject='" + subject + "'}";
    }
}