package com.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

// DTO yêu cầu tạo cuộc trò chuyện nhóm mới
public class CreateGroupConversationRequest {

    @NotBlank(message = "Tên nhóm không được để trống")
    @Size(max = 100, message = "Tên nhóm không được vượt quá 100 ký tự")
    private String name;

    @NotEmpty(message = "Danh sách thành viên không được để trống")
    private List<String> memberUsernames;

    public CreateGroupConversationRequest() {
    }

    public CreateGroupConversationRequest(String name, List<String> memberUsernames) {
        this.name = name;
        this.memberUsernames = memberUsernames;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getMemberUsernames() {
        return memberUsernames;
    }

    public void setMemberUsernames(List<String> memberUsernames) {
        this.memberUsernames = memberUsernames;
    }
}
