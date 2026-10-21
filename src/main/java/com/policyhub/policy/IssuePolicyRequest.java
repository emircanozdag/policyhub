package com.policyhub.policy;

import jakarta.validation.constraints.NotBlank;

public record IssuePolicyRequest(@NotBlank String quoteNumber) {
}
