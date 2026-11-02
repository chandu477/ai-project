package com.example.users.user;

import com.example.users.common.InvalidQueryParameterException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    static final int PAGE_SIZE = 20;

    private final AppUserRepository userRepository;

    public UserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> findUsers(int page, String sort, String direction) {
        UserSortField sortField = UserSortField.fromParam(sort);
        Sort.Direction sortDirection = Sort.Direction.fromOptionalString(direction)
                .orElseThrow(() -> new InvalidQueryParameterException("direction", direction, "asc, desc"));

        // Unsafe sort is required for the COUNT(o) aggregate; expressions come only from the
        // UserSortField whitelist. Sorting by id as a tie-breaker keeps pages stable.
        Sort order = JpaSort.unsafe(sortDirection, sortField.expression())
                .andUnsafe(Sort.Direction.ASC, "u.id");

        return PageResponse.from(userRepository.findUserSummaries(PageRequest.of(page, PAGE_SIZE, order)));
    }
}
