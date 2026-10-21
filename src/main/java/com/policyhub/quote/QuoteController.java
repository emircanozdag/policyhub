package com.policyhub.quote;

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
@RequestMapping("/api/v1/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PostMapping
    public ResponseEntity<QuoteResponse> create(@Valid @RequestBody CreateQuoteRequest request) {
        QuoteResponse quote = quoteService.createQuote(request);
        return ResponseEntity.created(URI.create("/api/v1/quotes/" + quote.quoteNumber())).body(quote);
    }

    @GetMapping("/{quoteNumber}")
    public QuoteResponse get(@PathVariable String quoteNumber) {
        return quoteService.getQuote(quoteNumber);
    }
}
