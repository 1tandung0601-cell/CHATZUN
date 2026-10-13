package com.chatapp.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

// DTO yêu cầu thêm thành viên vào nhóm
public class AddMembersRequest {

    @NotEmpty(message = "Danh sách thành viên cần thêm không được để trống")
    private List<String> usernames;

    public AddMembersRequest() {
    }

    public AddMembersRequest(List<String> usernames) {
        this.usernames = usernames;
    }

    public List<String> getUsernames() {
        return usernames;
    }

    public void setUsernames(List<String> usernames) {
        this.usernames = usernames;
    }
}
