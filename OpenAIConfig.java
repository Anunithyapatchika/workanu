/*
 * ============================================================================
 * FILE TO CREATE: OpenAIConfig.java
 * ============================================================================
 * 
 * LOCATION: 
 * src/main/java/com/uhg/comm/edelivery/reportingservice/config/OpenAIConfig.java
 * 
 * WHY THIS IS NEEDED:
 * - OpenAIServiceImpl.java imports this class (line 6)
 * - Without it, your code won't compile
 * - This wraps OpenAIPropertiesUtil and provides API key access
 * 
 * WHAT TO DO:
 * 1. Create new file at location above
 * 2. Copy ALL code below (everything after this comment block)
 * 3. Save
 * 4. Build: mvn clean package
 * 5. Should compile successfully!
 * 
 * ============================================================================
 */

package com.uhg.comm.edelivery.reportingservice.config;

import com.uhg.comm.edelivery.reportingservice.util.OpenAIPropertiesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for OpenAI integration.
 * 
 * This class wraps OpenAIPropertiesUtil and provides access to:
 * - OpenAI API configuration (URL, endpoint)
 * - Azure Search configuration for each query type (knowledge, monitoring, RCA)
 * - API key from Azure Key Vault
 * 
 * The configuration values are loaded from:
 * 1. application-stage2.yml (for most settings)
 * 2. Azure App Configuration (overrides YAML if configured)
 * 3. Azure Key Vault (for API key secret)
 * 
 * @author Your Team
 * @version 1.0
 */
@Configuration
public class OpenAIConfig {
    
    @Autowired
    private OpenAIPropertiesUtil openAIProperties;
    
    /**
     * API key loaded from Azure Key Vault or application properties.
     * Format in Key Vault: openai-api-key
     * If not found, defaults to empty string.
     */
    @Value("${openai-api-key:}")
    private String apiKey;
    
    /**
     * Get the OpenAI API URL including deployment and API version.
     * Example: https://openai-ecedel-aipoc.openai.azure.com/openai/deployments/gpt-4.1/chat/completions?api-version=2024-12-01-preview
     * 
     * @return OpenAI API endpoint URL
     */
    public String getUrl() {
        return openAIProperties.getUrl();
    }
    
    /**
     * Get the Azure Cognitive Search endpoint.
     * Example: https://aisearch-ecedel-aipoc.search.windows.net
     * 
     * @return Azure Search endpoint URL
     */
    public String getEndpoint() {
        return openAIProperties.getEndpoint();
    }
    
    /**
     * Get the OpenAI API key from Azure Key Vault.
     * This is used in the api-key header for authentication.
     * 
     * @return API key string
     */
    public String getApiKey() {
        return apiKey;
    }
    
    /**
     * Get configuration for knowledge base search.
     * Includes: index name, semantic config, topN documents, temperature, etc.
     * 
     * @return SearchConfig object with knowledge search settings
     */
    public OpenAIPropertiesUtil.SearchConfig getKnowledgeSearch() {
        return openAIProperties.getKnowledgeSearch();
    }
    
    /**
     * Get configuration for monitoring/logs search.
     * Optimized for diagnostic and log analysis queries.
     * 
     * @return SearchConfig object with monitoring search settings
     */
    public OpenAIPropertiesUtil.SearchConfig getMonitoringSearch() {
        return openAIProperties.getMonitoringSearch();
    }
    
    /**
     * Get configuration for RCA (Root Cause Analysis) search.
     * Optimized for incident investigation and RCA queries.
     * 
     * @return SearchConfig object with RCA search settings
     */
    public OpenAIPropertiesUtil.SearchConfig getRcaSearch() {
        return openAIProperties.getRcaSearch();
    }
}
