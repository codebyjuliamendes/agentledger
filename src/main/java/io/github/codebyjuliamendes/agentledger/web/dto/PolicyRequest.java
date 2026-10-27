package io.github.codebyjuliamendes.agentledger.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Term lists are validated by the domain ({@code RiskPolicy}) to keep one source of truth. */
public record PolicyRequest(
    @NotNull(message = "Provide arrays of up to 30 risk terms, each 2–40 characters.")
        List<String> medium,
    @NotNull(message = "Provide arrays of up to 30 risk terms, each 2–40 characters.")
        List<String> high) {}
