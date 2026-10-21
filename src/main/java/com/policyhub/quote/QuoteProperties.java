package com.policyhub.quote;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("policyhub.quote")
public record QuoteProperties(int validityDays) {
}
