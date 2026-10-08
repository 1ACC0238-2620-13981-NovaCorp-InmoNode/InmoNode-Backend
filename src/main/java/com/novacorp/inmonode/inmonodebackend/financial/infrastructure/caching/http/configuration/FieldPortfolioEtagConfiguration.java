package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.caching.http.configuration;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * US-39, Scenario 2: the field portfolio carries an ETag computed from its content. When the app sends it
 * back in If-None-Match and nothing changed, the answer is 304 Not Modified without a body, which saves the
 * agent's mobile data. Registered only for the portfolio, the one large download that is repeated daily.
 *
 * <p>The ETag is weak ({@code W/"..."}) because the same content is sent either GZIP-compressed or not
 * (US-39, Scenario 1), and Tomcat never compresses a response that carries a strong ETag.</p>
 */
@Configuration
public class FieldPortfolioEtagConfiguration {

    static final String PORTFOLIO_PATH = "/api/v1/field-sync/portfolio";

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> fieldPortfolioEtagFilter() {
        var filter = new ShallowEtagHeaderFilter();
        filter.setWriteWeakETag(true);
        var registration = new FilterRegistrationBean<>(filter);
        registration.setName("fieldPortfolioEtagFilter");
        registration.addUrlPatterns(PORTFOLIO_PATH);
        return registration;
    }
}
