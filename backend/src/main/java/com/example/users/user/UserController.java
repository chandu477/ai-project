package com.example.users.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User listing and creation")
public class UserController {

    private static final String VALIDATION_ERROR_EXAMPLE = """
            {"type": "about:blank", "title": "Invalid request", "status": 400, "detail": "Validation failed",
             "instance": "/api/users", "errors": {"monthlySalary": "At most 10 digits and 2 decimals"}}""";

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

    @GetMapping("/{id}")
    @Operation(summary = "Get a user", description = "Returns one user with the order count, salary and experience.")
    @ApiResponse(responseCode = "200", description = "The user")
    @ApiResponse(responseCode = "404", description = "No user with this ID",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    public UserSummary getUser(
            @Parameter(description = "User ID", example = "42") @PathVariable Long id) {
        return userService.findUser(id);
    }

    @PostMapping
    @Operation(summary = "Create a user",
            description = "Creates a user with no orders. The Location header points to the new user.")
    @ApiResponse(responseCode = "201", description = "User created")
    @ApiResponse(responseCode = "400",
            description = "Invalid username, experience or salary; the errors property maps each field to its message",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = VALIDATION_ERROR_EXAMPLE)))
    @ApiResponse(responseCode = "409", description = "Username already exists",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<UserSummary> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserSummary created = userService.createUser(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
        return ResponseEntity.created(location).body(created);
    }
}
