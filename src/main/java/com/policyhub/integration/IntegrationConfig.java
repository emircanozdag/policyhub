package com.policyhub.integration;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** One {@link RestClient} per partner, each with its own timeouts and credentials. */
@Configuration
public class IntegrationConfig {

    @Bean
    RestClient vehicleRegistryRestClient(RestClient.Builder builder, IntegrationProperties properties) {
        return restClient(builder, properties.vehicleRegistry());
    }

    @Bean
    RestClient paymentGatewayRestClient(RestClient.Builder builder, IntegrationProperties properties) {
        return restClient(builder, properties.paymentGateway());
    }

    /** Public so adapter tests can build exactly the client production uses. */
    public static RestClient restClient(RestClient.Builder builder, IntegrationProperties.Endpoint endpoint) {
        HttpClient httpClient = HttpClient.newBuilder()
                // The JDK client otherwise tries an h2c upgrade on plain-HTTP requests with a body, which
                // many partner gateways and proxies answer by resetting the stream. HTTP/1.1 is the safe common denominator.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(endpoint.connectTimeout())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(endpoint.readTimeout());

        RestClient.Builder configured = builder.clone()
                .baseUrl(endpoint.baseUrl().toString())
                .requestFactory(requestFactory);
        if (StringUtils.hasText(endpoint.apiKey())) {
            configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + endpoint.apiKey());
        }
        return configured.build();
    }
}
