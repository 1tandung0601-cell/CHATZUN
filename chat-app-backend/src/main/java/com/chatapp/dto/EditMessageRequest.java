package com.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Dữ liệu client gửi lên khi sửa tin nhắn.
public class EditMessageRequest {

    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    @Size(max = 10000, message = "Nội dung tin nhắn không được vượt quá 10000 ký tự")
    private String content;

    public EditMessageRequest() {
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
