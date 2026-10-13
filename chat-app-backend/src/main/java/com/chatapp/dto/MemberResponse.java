package com.chatapp.dto;

import com.chatapp.entity.MemberRole;
import java.time.LocalDateTime;

// DTO trả về thông tin thành viên của cuộc trò chuyện
public record MemberResponse(
        Long userId,
        String username,
        MemberRole role,
        LocalDateTime joinedAt
) {
}
