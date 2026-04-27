package com.highvia.productservice.config;

import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
import org.springframework.boot.autoconfigure.elasticsearch.RestClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * AWS OpenSearch compatibility fixes for the Elasticsearch Java client:
 * 1. Request interceptor: replaces vendor MIME type headers (which cause 406)
 *    with plain application/json.
 * 2. Response interceptor: injects X-Elastic-Product: Elasticsearch header
 *    that OpenSearch omits but the ES client requires.
 * Only active on the prod profile — local dev uses the standard ES client.
 */
@Configuration
@Profile("prod")
public class OpenSearchConfig {

    @Bean
    public RestClientBuilderCustomizer opensearchHeaderCustomizer() {
        return builder -> builder.setHttpClientConfigCallback(
            httpClientBuilder -> httpClientBuilder
                .addInterceptorFirst(
                    (HttpRequestInterceptor) (request, context) -> {
                        request.removeHeaders("Accept");
                        request.removeHeaders("Content-Type");
                        request.addHeader("Accept", "application/json");
                        request.addHeader("Content-Type", "application/json");
                    }
                )
                .addInterceptorLast(
                    (HttpResponseInterceptor) (response, context) ->
                        response.addHeader("X-Elastic-Product", "Elasticsearch")
                )
        );
    }
}
