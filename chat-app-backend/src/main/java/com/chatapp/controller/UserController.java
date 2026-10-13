package com.chatapp.controller;

import com.chatapp.dto.UserSearchResponse;
import com.chatapp.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Tìm theo username/email nhưng chỉ trả về các trường hiển thị cần thiết.
    // GET /api/users/search?query=...
    @GetMapping("/search")
    public ResponseEntity<Page<UserSearchResponse>> searchUsers(
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        if (page < 0 || size < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page phải >= 0 và size phải >= 1"
            );
        }

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        String keyword = query != null ? query.trim() : "";

        if (keyword.isEmpty()) {
            return ResponseEntity.ok(Page.empty(pageable));
        }

        return ResponseEntity.ok(userRepository.searchByUsernameOrEmail(
                keyword,
                authentication.getName(),
                pageable
        ));
    }
}
