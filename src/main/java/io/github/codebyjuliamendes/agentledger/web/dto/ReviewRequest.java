package io.github.codebyjuliamendes.agentledger.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
    @NotNull(message = "Choose approve or deny.")
        @Pattern(regexp = "approve|deny", message = "Choose approve or deny.")
        String decision,
    @Size(max = 120, message = "Reviewer name is too long (max 120).") String reviewer,
    @Size(max = 500, message = "Note is too long (max 500).") String note) {}
