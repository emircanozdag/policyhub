package com.policyhub.policy;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping
    public ResponseEntity<PolicyResponse> issue(@Valid @RequestBody IssuePolicyRequest request) {
        PolicyResponse policy = policyService.issueFromQuote(request.quoteNumber());
        return ResponseEntity.created(URI.create("/api/v1/policies/" + policy.policyNumber())).body(policy);
    }

    @GetMapping("/{policyNumber}")
    public PolicyResponse get(@PathVariable String policyNumber) {
        return policyService.getPolicy(policyNumber);
    }

    @PostMapping("/{policyNumber}/cancel")
    public PolicyResponse cancel(@PathVariable String policyNumber) {
        return policyService.cancel(policyNumber);
    }
}
