package com.example.users.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @Query(value = """
            SELECT new com.example.users.user.UserSummary(
                u.id, u.username, COUNT(o), u.monthlySalary, u.yearsOfExperience)
            FROM AppUser u
            LEFT JOIN CustomerOrder o ON o.user = u
            GROUP BY u.id, u.username, u.monthlySalary, u.yearsOfExperience
            """,
            countQuery = "SELECT COUNT(u) FROM AppUser u")
    Page<UserSummary> findUserSummaries(Pageable pageable);
}
