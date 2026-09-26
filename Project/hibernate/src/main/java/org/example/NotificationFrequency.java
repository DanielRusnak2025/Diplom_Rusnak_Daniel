package org.example;

/**
 * Частота уведомлений пользователя.
 * value — строка, хранимая в БД.
 */
public enum NotificationFrequency {

    OFTEN ("often",  15,  300),
    RARELY("rarely", 50,  900),
    NEVER ("never",  75, 1800);

    private final String value;
    private final int    likeThreshold;
    private final int    pollIntervalSec;

    NotificationFrequency(String value, int likeThreshold, int pollIntervalSec) {
        this.value          = value;
        this.likeThreshold  = likeThreshold;
        this.pollIntervalSec = pollIntervalSec;
    }

    public String getValue()        { return value; }
    public int getLikeThreshold()   { return likeThreshold; }
    public int getPollIntervalSec() { return pollIntervalSec; }

    public static NotificationFrequency fromString(String s) {
        if (s == null) return NEVER;
        for (NotificationFrequency f : values()) {
            if (f.value.equalsIgnoreCase(s)) return f;
        }
        return NEVER;
    }

    @Override
    public String toString() { return value; }
}
