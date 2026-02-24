package com.uhg.comm.edelivery.reportingservice.config;

import com.uhg.comm.edelivery.reportingservice.util.OpenAIPropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class for OpenAI integration.
 * Handles API keys from Azure Key Vault and configuration from OpenAIPropertiesUtil.
 */
@Configuration
public class OpenAIConfig {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAIConfig.class);
    
    @Autowired
    private OpenAIPropertiesUtil openAIProperties;
    
    /**
     * API key from Azure Key Vault.
     * Tries multiple possible key names for flexibility.
     */
    @Value("${openai-api-key:}")
    private String apiKey;
    
    /**
     * Alternative API key name (some environments use this)
     */
    @Value("${openai.api.key:}")
    private String apiKeyAlt;
    
    /**
     * Another alternative (Azure OpenAI specific)
     */
    @Value("${azure.openai.key:}")
    private String azureOpenAIKey;
    
    /**
     * Verify configuration on startup
     */
    @PostConstruct
    public void init() {
        String effectiveKey = getApiKey();
        
        if (effectiveKey == null || effectiveKey.trim().isEmpty()) {
            LOGGER.error("🔴 CRITICAL: OpenAI API Key is NOT configured!");
            LOGGER.error("🔴 Checked key names: openai-api-key, openai.api.key, azure.openai.key");
            LOGGER.error("🔴 Please configure API key in Azure Key Vault or application properties");
        } else {
            // Log that key exists (but don't log the actual key!)
            String maskedKey = effectiveKey.substring(0, Math.min(4, effectiveKey.length())) + "****";
            LOGGER.info("✅ OpenAI API Key loaded successfully ({}...)", maskedKey);
        }
        
        LOGGER.info("✅ OpenAI URL: {}", openAIProperties.getUrl());
        LOGGER.info("✅ Azure Search Endpoint: {}", openAIProperties.getEndpoint());
    }
    
    public String getUrl() {
        return openAIProperties.getUrl();
    }
    
    public String getEndpoint() {
        return openAIProperties.getEndpoint();
    }
    
    /**
     * Get API key, trying multiple possible sources.
     * Priority:
     * 1. openai-api-key (Azure Key Vault standard)
     * 2. openai.api.key (alternative format)
     * 3. azure.openai.key (Azure specific)
     */
    public String getApiKey() {
        // Try primary key name
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            return apiKey;
        }
        
        // Try alternative format
        if (apiKeyAlt != null && !apiKeyAlt.trim().isEmpty()) {
            return apiKeyAlt;
        }
        
        // Try Azure-specific name
        if (azureOpenAIKey != null && !azureOpenAIKey.trim().isEmpty()) {
            return azureOpenAIKey;
        }
        
        // No key found
        return "";
    }
    
    public OpenAIPropertiesUtil.SearchConfig getKnowledgeSearch() {
        return openAIProperties.getKnowledgeSearch();
    }
    
    public OpenAIPropertiesUtil.SearchConfig getMonitoringSearch() {
        return openAIProperties.getMonitoringSearch();
    }
    
    public OpenAIPropertiesUtil.SearchConfig getRcaSearch() {
        return openAIProperties.getRcaSearch();
    }
}
