package com.chatapp.repository;

import com.chatapp.dto.UserSearchResponse;
import com.chatapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

        @Query(
            value = """
                select new com.chatapp.dto.UserSearchResponse(u.id, u.username)
                from User u
                where lower(u.username) <> lower(:currentUsername)
                  and (lower(u.username) like lower(concat('%', :query, '%'))
                   or lower(u.email) like lower(concat('%', :query, '%')))
                order by lower(u.username), u.id
                """,
            countQuery = """
                select count(u)
                from User u
                where lower(u.username) <> lower(:currentUsername)
                  and (lower(u.username) like lower(concat('%', :query, '%'))
                   or lower(u.email) like lower(concat('%', :query, '%')))
                """
        )
        Page<UserSearchResponse> searchByUsernameOrEmail(
            @Param("query") String query,
            @Param("currentUsername") String currentUsername,
            Pageable pageable
        );
}