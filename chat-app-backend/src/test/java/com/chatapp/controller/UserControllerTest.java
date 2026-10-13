package com.chatapp.controller;

import com.chatapp.dto.UserSearchResponse;
import com.chatapp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserController userController;

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("current", null, List.of());

    @Test
    void searchUsers_TrimsQueryAndReturnsPagedSafeResults() {
        PageRequest pageable = PageRequest.of(1, 20);
        UserSearchResponse result = new UserSearchResponse(42L, "Alice");
        when(userRepository.searchByUsernameOrEmail("ali", "current", pageable))
                .thenReturn(new PageImpl<>(List.of(result), pageable, 21));

        var response = userController.searchUsers("  ali  ", 1, 20, authentication);

        assertThat(response.getBody().getContent()).containsExactly(result);
        assertThat(response.getBody().getTotalElements()).isEqualTo(21);
        verify(userRepository).searchByUsernameOrEmail("ali", "current", pageable);
    }

    @Test
    void searchUsers_BlankQueryReturnsEmptyPageWithoutLoadingUsers() {
        var response = userController.searchUsers("  ", 0, 20, authentication);

        assertThat(response.getBody().getContent()).isEmpty();
        assertThat(response.getBody().getTotalElements()).isZero();
        verifyNoInteractions(userRepository);
    }

    @Test
    void searchUsers_RejectsNegativePage() {
        assertThatThrownBy(() -> userController.searchUsers("a", -1, 20, authentication))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void searchUsers_CapsRequestedPageSize() {
        PageRequest pageable = PageRequest.of(0, 50);
        when(userRepository.searchByUsernameOrEmail("alice", "current", pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        userController.searchUsers("alice", 0, 500, authentication);

        verify(userRepository).searchByUsernameOrEmail("alice", "current", pageable);
    }
}