package com.uhg.comm.edelivery.reportingservice.util;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Component
@RefreshScope    
@ConfigurationProperties(prefix = "openai")
@Data
public class OpenAIPropertiesUtil {
    private String url;
    private String endpoint;
    private SearchConfig knowledgeSearch;
    private SearchConfig monitoringSearch;
    private SearchConfig rcaSearch;

    @Data
    public static class SearchConfig {
        private String queryType;
        private String semanticConfiguration;
        private boolean inScope;
        private String roleInformation;
        private int strictness;
        private String indexName;
        private int topNDocuments;
        private double temperature;
        private int maxTokens;
        private double topP;
    }
}
