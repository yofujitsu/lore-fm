package ru.yofujitsu.lorefm.release.application;

public class InvalidSearchException extends IllegalArgumentException {
    private final String code;
    public InvalidSearchException(String code, String message) {
        super(message);
        this.code = code;
    }
    public String code() { return code; }
}
