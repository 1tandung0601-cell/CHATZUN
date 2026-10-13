package com.chatapp.dto;

public class AuthResponse {

    private boolean success;
    private String message;
    private String username;
    private String token;

    public AuthResponse(
            boolean success,
            String message,
            String username,
            String token) {

        this.success = success;
        this.message = message;
        this.username = username;
        this.token = token;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getUsername() {
        return username;
    }

    public String getToken() {
        return token;
    }
}