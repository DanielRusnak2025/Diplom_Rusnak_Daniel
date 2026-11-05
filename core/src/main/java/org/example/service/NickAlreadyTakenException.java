package org.example.service;

public class NickAlreadyTakenException extends IllegalArgumentException {
    public NickAlreadyTakenException(String nick) {
        super("Login is already taken: " + nick);
    }
}
