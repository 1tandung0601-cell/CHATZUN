package com.chatapp.service;

import com.chatapp.dto.ChatMessageEvent;
import com.chatapp.repository.ConversationMemberRepository;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

@Component
public class ConversationEventPublisher {

    private static final String USER_DESTINATION = "/queue/conversations";

    private final ConversationMemberRepository memberRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    public ConversationEventPublisher(
            ConversationMemberRepository memberRepository,
            SimpMessageSendingOperations messagingTemplate
    ) {
        this.memberRepository = memberRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public void publishToMembers(Long conversationId, ChatMessageEvent event) {
        memberRepository.findByConversation_Id(conversationId).forEach(member ->
                messagingTemplate.convertAndSendToUser(
                        member.getUser().getUsername(),
                        USER_DESTINATION,
                        event
                )
        );
    }
}