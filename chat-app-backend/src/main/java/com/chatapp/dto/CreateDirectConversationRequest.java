package com.chatapp.dto;

import jakarta.validation.constraints.NotBlank;

// Dữ liệu client gửi lên để mở chat 1-1.
public class CreateDirectConversationRequest {

    @NotBlank(message = "Participant username must not be blank")
    private String participantUsername;

    public String getParticipantUsername() {
        return participantUsername;
    }

    public void setParticipantUsername(String participantUsername) {
        this.participantUsername = participantUsername;
    }
}