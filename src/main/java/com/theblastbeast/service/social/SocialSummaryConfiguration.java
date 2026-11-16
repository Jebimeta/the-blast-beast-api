package com.theblastbeast.service.social;

import com.theblastbeast.properties.SocialSummaryProperties;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SocialSummaryConfiguration {

    @Bean
    RestClient socialSummaryRestClient(SocialSummaryProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl().replaceAll("/+$", ""))
                .requestFactory(requestFactory)
                .build();
    }
}
