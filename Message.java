package com.uhg.comm.edelivery.reportingservice.model;

import lombok.Data;

/**
 * Model class representing a message in the OpenAI conversation.
 * Used for both user messages and assistant responses.
 * 
 * This class follows the OpenAI API message format with role and content.
 * 
 * @author Your Team
 * @version 1.0
 */
@Data
public class Message {
    
    /**
     * Role of the message sender.
     * Possible values:
     * - "user": Message from the user
     * - "assistant": Message from Claude/OpenAI assistant
     * - "system": System message (instructions to the AI)
     */
    private String role;
    
    /**
     * Content of the message.
     * The actual text content of the message.
     */
    private String content;
    
    /**
     * Default constructor.
     * Required for Jackson deserialization.
     */
    public Message() {
    }
    
    /**
     * Constructor with role and content.
     * 
     * @param role The message role (user/assistant/system)
     * @param content The message content text
     */
    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }
    
    /**
     * Creates a user message.
     * 
     * @param content The user's message content
     * @return Message object with role="user"
     */
    public static Message userMessage(String content) {
        return new Message("user", content);
    }
    
    /**
     * Creates an assistant message.
     * 
     * @param content The assistant's message content
     * @return Message object with role="assistant"
     */
    public static Message assistantMessage(String content) {
        return new Message("assistant", content);
    }
    
    /**
     * Creates a system message.
     * 
     * @param content The system message content
     * @return Message object with role="system"
     */
    public static Message systemMessage(String content) {
        return new Message("system", content);
    }
}
