package com.example.users.user;

import com.example.users.common.InvalidQueryParameterException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;

@Service
public class UserService {

    static final int PAGE_SIZE = 20;

    private final AppUserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(AppUserRepository userRepository, ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
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

    @Transactional(readOnly = true)
    public UserSummary findUser(Long id) {
        return userRepository.findUserSummaryById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * Deliberately not @Transactional: the insert commits inside saveAndFlush, so a unique-constraint
     * race surfaces here as DataIntegrityViolationException, and UserCreatedEvent is published only
     * after the new user is committed (listeners therefore run on committed data).
     */
    public UserSummary createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException(request.username());
        }
        AppUser user = new AppUser(request.username(), request.monthlySalary(),
                request.yearsOfExperience().setScale(1, RoundingMode.UNNECESSARY));
        UserSummary created;
        try {
            created = toSummary(userRepository.saveAndFlush(user), 0);
        } catch (DataIntegrityViolationException ex) {
            // Only a lost race on the unique username is a conflict; other violations are real errors.
            if (userRepository.existsByUsername(request.username())) {
                throw new UsernameAlreadyExistsException(request.username());
            }
            throw ex;
        }
        eventPublisher.publishEvent(new UserCreatedEvent(created.id()));
        return created;
    }

    private static UserSummary toSummary(AppUser user, long orderCount) {
        return new UserSummary(user.getId(), user.getUsername(), orderCount,
                user.getMonthlySalary(), user.getYearsOfExperience());
    }
}
