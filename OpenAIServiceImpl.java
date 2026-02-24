package com.uhg.comm.edelivery.reportingservice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uhg.comm.edelivery.reportingservice.bean.MessageWithImageResponse;
import com.uhg.comm.edelivery.reportingservice.config.OpenAIConfig;
import com.uhg.comm.edelivery.reportingservice.model.Image;
import com.uhg.comm.edelivery.reportingservice.model.Message;
import com.uhg.comm.edelivery.reportingservice.service.OpenAIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class OpenAIServiceImpl implements OpenAIService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAIServiceImpl.class);
    private static final Pattern PRINTRAIL_PATTERN = Pattern.compile("\\bPT\\d{4}\\b", Pattern.CASE_INSENSITIVE);

    @Autowired
    private OpenAIConfig openAIConfig;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public MessageWithImageResponse callOpenAIApi(List<Message> conversationHistory, String queryType) {
        try {
            LOGGER.info("=== STARTING OPENAI API CALL ===");
            LOGGER.info("Query Type: {}", queryType);
            LOGGER.info("Conversation History Size: {}", conversationHistory.size());

            // STEP 1: Enhance conversation with context if needed
            List<Message> enhancedHistory = enhanceConversationWithContext(conversationHistory);

            // STEP 2: Get configuration based on query type
            Map<String, Object> config = getConfigForQueryType(queryType);
            
            LOGGER.info("Config - Index: {}, TopN: {}, Temp: {}, Strictness: {}, InScope: {}, MaxTokens: {}", 
                config.get("indexName"), 
                config.get("topNDocuments"), 
                config.get("temperature"),
                config.get("strictness"),
                config.get("inScope"),
                config.get("maxTokens"));

            // STEP 3: Build request payload
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode requestBody = mapper.createObjectNode();

            ArrayNode messagesArray = mapper.createArrayNode();
            
            // Add system message with role information
            ObjectNode systemMessage = mapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", (String) config.get("roleInformation"));
            messagesArray.add(systemMessage);

            // Add conversation history
            for (Message msg : enhancedHistory) {
                ObjectNode messageNode = mapper.createObjectNode();
                messageNode.put("role", msg.getRole());
                messageNode.put("content", msg.getContent());
                messagesArray.add(messageNode);
            }

            requestBody.set("messages", messagesArray);
            requestBody.put("temperature", (Double) config.get("temperature"));
            requestBody.put("top_p", (Double) config.get("topP"));
            requestBody.put("max_tokens", (Integer) config.get("maxTokens"));

            // Data sources configuration
            ArrayNode dataSources = mapper.createArrayNode();
            ObjectNode dataSource = mapper.createObjectNode();
            dataSource.put("type", "azure_search");

            ObjectNode parameters = mapper.createObjectNode();
            parameters.put("endpoint", openAIConfig.getEndpoint());
            parameters.put("index_name", (String) config.get("indexName"));
            parameters.put("semantic_configuration", (String) config.get("semanticConfiguration"));
            parameters.put("query_type", (String) config.get("queryType"));
            parameters.put("in_scope", (Boolean) config.get("inScope"));
            parameters.put("strictness", (Integer) config.get("strictness"));
            parameters.put("top_n_documents", (Integer) config.get("topNDocuments"));
            parameters.put("role_information", (String) config.get("roleInformation"));

            dataSource.set("parameters", parameters);
            dataSources.add(dataSource);
            requestBody.set("data_sources", dataSources);

            // STEP 4: Make API call
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", openAIConfig.getApiKey());

            HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);

            LOGGER.info("Sending request to OpenAI API...");
            ResponseEntity<String> response = restTemplate.exchange(
                openAIConfig.getUrl(),
                HttpMethod.POST,
                request,
                String.class
            );

            // STEP 5: Process response
            if (response.getStatusCode() == HttpStatus.OK) {
                LOGGER.info("Successfully received response from OpenAI");
                return processOpenAIResponse(response.getBody());
            } else {
                LOGGER.error("OpenAI API returned non-OK status: {}", response.getStatusCode());
                throw new RuntimeException("OpenAI API call failed with status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            LOGGER.error("Error calling OpenAI API", e);
            throw new RuntimeException("Error processing request: " + e.getMessage(), e);
        }
    }

    /**
     * CRITICAL: Enhance conversation with context for vague questions
     * This makes "tell me more", "it", "this", etc. reference the correct printrail
     */
    private List<Message> enhanceConversationWithContext(List<Message> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return conversationHistory;
        }

        // Get the last user message
        Message lastUserMessage = null;
        for (int i = conversationHistory.size() - 1; i >= 0; i--) {
            if ("user".equals(conversationHistory.get(i).getRole())) {
                lastUserMessage = conversationHistory.get(i);
                break;
            }
        }

        if (lastUserMessage == null) {
            return conversationHistory;
        }

        String question = lastUserMessage.getContent();
        
        // Check if this is a vague question that needs context
        if (isGenericFollowUpQuestion(question) || usesPronounsWithoutContext(question)) {
            LOGGER.info("🎯 DETECTED GENERIC FOLLOW-UP OR VAGUE PRONOUN - Enhancing with context");
            
            // Extract recent context (printrail or topic)
            String context = extractRecentContext(conversationHistory);
            
            if (context != null && !context.isEmpty()) {
                LOGGER.info("🎯 Found recent context: {}", context);
                
                // Create enhanced message
                String enhancedQuestion = "[Context: User is asking about " + context + "] " + question;
                LOGGER.info("🎯 Enhanced question: {}", enhancedQuestion);
                
                // Create new history with enhanced last message
                List<Message> enhancedHistory = new ArrayList<>(conversationHistory);
                Message enhancedMessage = new Message();
                enhancedMessage.setRole("user");
                enhancedMessage.setContent(enhancedQuestion);
                
                // Replace last user message
                for (int i = enhancedHistory.size() - 1; i >= 0; i--) {
                    if ("user".equals(enhancedHistory.get(i).getRole())) {
                        enhancedHistory.set(i, enhancedMessage);
                        break;
                    }
                }
                
                return enhancedHistory;
            }
        }

        return conversationHistory;
    }

    /**
     * Detect generic follow-up questions like "tell me more", "expand", etc.
     */
    private boolean isGenericFollowUpQuestion(String question) {
        if (question == null) return false;
        
        String lowerQuestion = question.toLowerCase().trim();
        
        String[] genericPatterns = {
            "tell me more",
            "expand",
            "elaborate",
            "more details",
            "more information",
            "continue",
            "what else",
            "go on",
            "keep going",
            "explain further",
            "tell me about it",
            "more about it"
        };
        
        for (String pattern : genericPatterns) {
            if (lowerQuestion.equals(pattern) || lowerQuestion.startsWith(pattern)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Detect questions that use pronouns without clear context
     */
    private boolean usesPronounsWithoutContext(String question) {
        if (question == null) return false;
        
        String lowerQuestion = question.toLowerCase();
        
        // Check if question starts with or contains vague pronouns
        String[] vagueStarters = {
            "it ", "this ", "that ", "these ", "those ", "them ",
            "about it", "for it", "with it", "on it",
            "about this", "for this", "with this",
            "about that", "for that", "with that"
        };
        
        for (String starter : vagueStarters) {
            if (lowerQuestion.startsWith(starter) || lowerQuestion.contains(" " + starter)) {
                // Check if there's a specific technical noun nearby
                if (!containsSpecificNoun(question)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Check if question contains specific technical nouns
     */
    private boolean containsSpecificNoun(String question) {
        String lowerQuestion = question.toLowerCase();
        String[] specificNouns = {
            "preprocessor", "processor", "handler", "service",
            "workflow", "architecture", "printrail", "trail",
            "job", "file", "document", "data"
        };
        
        for (String noun : specificNouns) {
            if (lowerQuestion.contains(noun)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Extract recent context (printrail or topic) from conversation
     */
    private String extractRecentContext(List<Message> conversationHistory) {
        // Priority 1: Look for printrails in recent messages (PT####)
        for (int i = conversationHistory.size() - 1; i >= Math.max(0, conversationHistory.size() - 6); i--) {
            Message msg = conversationHistory.get(i);
            Matcher matcher = PRINTRAIL_PATTERN.matcher(msg.getContent());
            if (matcher.find()) {
                return matcher.group(); // Return first printrail found
            }
        }
        
        // Priority 2: Look for key topics in assistant messages
        for (int i = conversationHistory.size() - 1; i >= Math.max(0, conversationHistory.size() - 4); i--) {
            Message msg = conversationHistory.get(i);
            if ("assistant".equals(msg.getRole())) {
                String topic = extractKeyTopicFromAssistantMessage(msg.getContent());
                if (topic != null) {
                    return topic;
                }
            }
        }
        
        // Priority 3: Look for key topics in user messages
        for (int i = conversationHistory.size() - 2; i >= Math.max(0, conversationHistory.size() - 4); i--) {
            Message msg = conversationHistory.get(i);
            if ("user".equals(msg.getRole())) {
                String topic = extractKeyTopicFromUserMessage(msg.getContent());
                if (topic != null) {
                    return topic;
                }
            }
        }
        
        return null;
    }

    /**
     * Extract key topic from assistant message
     */
    private String extractKeyTopicFromAssistantMessage(String content) {
        if (content == null) return null;
        
        // Look for mentions of services, processors, etc.
        Pattern servicePattern = Pattern.compile("(\\w+\\s+(?:service|preprocessor|processor|handler|workflow))", Pattern.CASE_INSENSITIVE);
        Matcher matcher = servicePattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Look for "for PT####"
        Pattern forPrintrailPattern = Pattern.compile("for\\s+(PT\\d{4})", Pattern.CASE_INSENSITIVE);
        matcher = forPrintrailPattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }

    /**
     * Extract key topic from user message
     */
    private String extractKeyTopicFromUserMessage(String content) {
        if (content == null) return null;
        
        String lowerContent = content.toLowerCase();
        
        String[] keywords = {
            "preprocessor", "processor", "handler", "service",
            "workflow", "architecture", "edelivery"
        };
        
        for (String keyword : keywords) {
            if (lowerContent.contains(keyword)) {
                return keyword;
            }
        }
        
        return null;
    }

    /**
     * Get configuration based on query type
     */
    private Map<String, Object> getConfigForQueryType(String queryType) {
        Map<String, Object> config = new HashMap<>();
        
        switch (queryType.toLowerCase()) {
            case "knowledge":
                config.put("indexName", openAIConfig.getKnowledgeSearch().getIndexName());
                config.put("semanticConfiguration", openAIConfig.getKnowledgeSearch().getSemanticConfiguration());
                config.put("queryType", openAIConfig.getKnowledgeSearch().getQueryType());
                config.put("inScope", openAIConfig.getKnowledgeSearch().isInScope());
                config.put("strictness", openAIConfig.getKnowledgeSearch().getStrictness());
                config.put("topNDocuments", openAIConfig.getKnowledgeSearch().getTopNDocuments());
                config.put("temperature", openAIConfig.getKnowledgeSearch().getTemperature());
                config.put("maxTokens", openAIConfig.getKnowledgeSearch().getMaxTokens());
                config.put("topP", openAIConfig.getKnowledgeSearch().getTopP());
                config.put("roleInformation", openAIConfig.getKnowledgeSearch().getRoleInformation());
                break;
                
            case "monitoring":
                config.put("indexName", openAIConfig.getMonitoringSearch().getIndexName());
                config.put("semanticConfiguration", openAIConfig.getMonitoringSearch().getSemanticConfiguration());
                config.put("queryType", openAIConfig.getMonitoringSearch().getQueryType());
                config.put("inScope", openAIConfig.getMonitoringSearch().isInScope());
                config.put("strictness", openAIConfig.getMonitoringSearch().getStrictness());
                config.put("topNDocuments", openAIConfig.getMonitoringSearch().getTopNDocuments());
                config.put("temperature", openAIConfig.getMonitoringSearch().getTemperature());
                config.put("maxTokens", openAIConfig.getMonitoringSearch().getMaxTokens());
                config.put("topP", openAIConfig.getMonitoringSearch().getTopP());
                config.put("roleInformation", openAIConfig.getMonitoringSearch().getRoleInformation());
                break;
                
            case "rca":
                config.put("indexName", openAIConfig.getRcaSearch().getIndexName());
                config.put("semanticConfiguration", openAIConfig.getRcaSearch().getSemanticConfiguration());
                config.put("queryType", openAIConfig.getRcaSearch().getQueryType());
                config.put("inScope", openAIConfig.getRcaSearch().isInScope());
                config.put("strictness", openAIConfig.getRcaSearch().getStrictness());
                config.put("topNDocuments", openAIConfig.getRcaSearch().getTopNDocuments());
                config.put("temperature", openAIConfig.getRcaSearch().getTemperature());
                config.put("maxTokens", openAIConfig.getRcaSearch().getMaxTokens());
                config.put("topP", openAIConfig.getRcaSearch().getTopP());
                config.put("roleInformation", openAIConfig.getRcaSearch().getRoleInformation());
                break;
                
            default:
                LOGGER.warn("Unknown query type: {}, using knowledge config", queryType);
                config.put("indexName", openAIConfig.getKnowledgeSearch().getIndexName());
                config.put("semanticConfiguration", openAIConfig.getKnowledgeSearch().getSemanticConfiguration());
                config.put("queryType", openAIConfig.getKnowledgeSearch().getQueryType());
                config.put("inScope", openAIConfig.getKnowledgeSearch().isInScope());
                config.put("strictness", openAIConfig.getKnowledgeSearch().getStrictness());
                config.put("topNDocuments", openAIConfig.getKnowledgeSearch().getTopNDocuments());
                config.put("temperature", openAIConfig.getKnowledgeSearch().getTemperature());
                config.put("maxTokens", openAIConfig.getKnowledgeSearch().getMaxTokens());
                config.put("topP", openAIConfig.getKnowledgeSearch().getTopP());
                config.put("roleInformation", openAIConfig.getKnowledgeSearch().getRoleInformation());
        }
        
        return config;
    }

    /**
     * Process OpenAI response and extract message and images
     */
    private MessageWithImageResponse processOpenAIResponse(String responseBody) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode data = mapper.readTree(responseBody);

        MessageWithImageResponse result = new MessageWithImageResponse();
        List<Image> images = new ArrayList<>();

        if (data.has("choices") && data.get("choices").size() > 0) {
            JsonNode choice = data.get("choices").get(0);
            JsonNode messageNode = choice.get("message");

            if (messageNode != null) {
                Message message = new Message();
                message.setRole(messageNode.get("role").asText());
                message.setContent(messageNode.get("content").asText());
                result.setMessage(message);

                // Extract images if present
                if (messageNode.has("context") && messageNode.get("context").has("citations")) {
                    JsonNode citations = messageNode.get("context").get("citations");
                    for (JsonNode citation : citations) {
                        if (citation.has("url") && citation.get("url").asText().toLowerCase().endsWith(".png")) {
                            Image image = new Image();
                            image.setUrl(citation.get("url").asText());
                            if (citation.has("title")) {
                                image.setTitle(citation.get("title").asText());
                            }
                            images.add(image);
                        }
                    }
                }
            }
        }

        result.setImages(images);
        return result;
    }
}
