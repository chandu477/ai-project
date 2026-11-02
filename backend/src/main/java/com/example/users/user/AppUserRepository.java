package com.example.users.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    String SUMMARY_SELECT = """
            SELECT new com.example.users.user.UserSummary(
                u.id, u.username, COUNT(o), u.monthlySalary, u.yearsOfExperience)
            FROM AppUser u
            LEFT JOIN CustomerOrder o ON o.user = u
            """;

    String SUMMARY_GROUP_BY = """
            GROUP BY u.id, u.username, u.monthlySalary, u.yearsOfExperience
            """;

    @Query(value = SUMMARY_SELECT + SUMMARY_GROUP_BY, countQuery = "SELECT COUNT(u) FROM AppUser u")
    Page<UserSummary> findUserSummaries(Pageable pageable);

    @Query(SUMMARY_SELECT + "WHERE u.id = :id\n" + SUMMARY_GROUP_BY)
    Optional<UserSummary> findUserSummaryById(@Param("id") Long id);

    boolean existsByUsername(String username);
}
