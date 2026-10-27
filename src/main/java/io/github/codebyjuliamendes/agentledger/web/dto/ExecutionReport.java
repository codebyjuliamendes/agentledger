package io.github.codebyjuliamendes.agentledger.web.dto;

import jakarta.validation.constraints.Size;

public record ExecutionReport(
    @Size(max = 120, message = "Actor name is too long (max 120).") String actor,
    @Size(max = 500, message = "Note is too long (max 500).") String note) {}
