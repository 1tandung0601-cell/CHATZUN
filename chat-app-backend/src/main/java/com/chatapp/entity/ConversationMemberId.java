
package com.chatapp.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

// Đại diện cho khóa chính kép của conversation_members.
@Embeddable
public class ConversationMemberId implements Serializable {

    private Long conversationId;
    private Long userId;

    public ConversationMemberId() {
    }

    public ConversationMemberId(Long conversationId, Long userId) {
        this.conversationId = conversationId;
        this.userId = userId;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ConversationMemberId that)) return false;

        return Objects.equals(conversationId, that.conversationId)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationId, userId);
    }
}