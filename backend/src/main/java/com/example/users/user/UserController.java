package com.example.users.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User listing")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List users",
            description = "Returns users 20 per page with their order count, monthly salary and years of experience, "
                    + "sorted by the given column.")
    @ApiResponse(responseCode = "200", description = "Page of users")
    @ApiResponse(responseCode = "400", description = "Invalid page, sort or direction",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    public PageResponse<UserSummary> listUsers(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Column to sort by",
                    schema = @Schema(allowableValues = {"username", "orderCount", "monthlySalary", "yearsOfExperience"}, defaultValue = "username"))
            @RequestParam(defaultValue = "username") String sort,

            @Parameter(description = "Sort direction",
                    schema = @Schema(allowableValues = {"asc", "desc"}, defaultValue = "asc"))
            @RequestParam(defaultValue = "asc") String direction) {
        return userService.findUsers(page, sort, direction);
    }
}
