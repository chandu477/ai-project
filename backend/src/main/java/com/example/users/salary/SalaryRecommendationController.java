package com.example.users.salary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/salary-recommendations")
@Tag(name = "Salary recommendations", description = "AI-assisted monthly salary proposals for new users")
public class SalaryRecommendationController {

    private static final String VALIDATION_ERROR_EXAMPLE = """
            {"type": "about:blank", "title": "Invalid request", "status": 400, "detail": "Validation failed",
             "instance": "/api/salary-recommendations",
             "errors": {"yearsOfExperience": "Years of experience must be between 0 and 60"}}""";

    private final SalaryRecommendationService recommendationService;

    public SalaryRecommendationController(SalaryRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping
    @Operation(summary = "Recommend a monthly salary",
            description = "Proposes a monthly salary for the given years of experience, based on salary statistics "
                    + "of existing users. Claude makes the proposal; if it is unavailable or implausible, a "
                    + "statistical estimate is returned (source STATISTICAL). It is a POST because an uncached "
                    + "call can cost money. Results are cached per experience until a user is created.")
    @ApiResponse(responseCode = "200", description = "The recommendation")
    @ApiResponse(responseCode = "400",
            description = "Invalid years of experience; the errors property maps each field to its message",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = VALIDATION_ERROR_EXAMPLE)))
    @ApiResponse(responseCode = "422", description = "There are no users to base a recommendation on",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    public SalaryRecommendation recommend(@Valid @RequestBody RecommendSalaryRequest request) {
        return recommendationService.recommend(request.yearsOfExperience());
    }
}
