package com.familyapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Configuration to ensure database connection parameters are set correctly,
 * especially for Railway deployments where MySQL might not be immediately available.
 */
@Configuration
public class DatasourceConfig {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        // Ensure connection parameters are present in the URL
        String enhancedUrl = enhanceConnectionUrl(datasourceUrl);
        
        return DataSourceBuilder.create()
                .url(enhancedUrl)
                .username(datasourceUsername)
                .password(datasourcePassword)
                .build();
    }

    private String enhanceConnectionUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        // Check if URL already has connection parameters
        boolean hasParams = url.contains("?");
        StringBuilder enhancedUrl = new StringBuilder(url);

        if (!hasParams) {
            enhancedUrl.append("?");
        } else {
            enhancedUrl.append("&");
        }

        // Add/ensure connection resilience parameters
        String[] params = {
            "useSSL=false",
            "allowPublicKeyRetrieval=true",
            "serverTimezone=UTC",
            "autoReconnect=true",
            "failOverReadOnly=false",
            "maxReconnects=10",
            "initialTimeout=2",
            "connectTimeout=60000",
            "socketTimeout=60000"
        };

        for (String param : params) {
            String key = param.split("=")[0];
            // Only add if not already present
            if (!enhancedUrl.toString().contains(key + "=")) {
                if (enhancedUrl.charAt(enhancedUrl.length() - 1) != '?' && 
                    enhancedUrl.charAt(enhancedUrl.length() - 1) != '&') {
                    enhancedUrl.append("&");
                }
                enhancedUrl.append(param);
            }
        }

        String result = enhancedUrl.toString();
        System.out.println("Enhanced datasource URL (connection parameters ensured)");
        return result;
    }
}

