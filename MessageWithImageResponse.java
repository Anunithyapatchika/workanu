package com.uhg.comm.edelivery.reportingservice.bean;

import com.uhg.comm.edelivery.reportingservice.model.Image;
import com.uhg.comm.edelivery.reportingservice.model.Message;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Response model from OpenAI API containing both message and images.
 * 
 * This bean encapsulates:
 * - The main message response from OpenAI
 * - Any images that were cited in the response (from Azure Search)
 * 
 * Images are extracted from Azure Search citations when documents
 * contain image references (e.g., .png files).
 * 
 * @author Your Team
 * @version 1.0
 */
@Data
public class MessageWithImageResponse {
    
    /**
     * The main message response from OpenAI.
     * Contains the role (always "assistant") and content (the actual response text).
     */
    private Message message;
    
    /**
     * List of images included in the response.
     * Images come from Azure Search citations when the response
     * references documents that contain images.
     * Can be empty if no images were found.
     */
    private List<Image> images;
    
    /**
     * Default constructor.
     * Initializes images list as empty ArrayList.
     */
    public MessageWithImageResponse() {
        this.images = new ArrayList<>();
    }
    
    /**
     * Constructor with message only.
     * Initializes images as empty list.
     * 
     * @param message The OpenAI message response
     */
    public MessageWithImageResponse(Message message) {
        this.message = message;
        this.images = new ArrayList<>();
    }
    
    /**
     * Constructor with message and images.
     * 
     * @param message The OpenAI message response
     * @param images List of images from citations
     */
    public MessageWithImageResponse(Message message, List<Image> images) {
        this.message = message;
        this.images = images != null ? images : new ArrayList<>();
    }
    
    /**
     * Add an image to the response.
     * 
     * @param image The image to add
     */
    public void addImage(Image image) {
        if (this.images == null) {
            this.images = new ArrayList<>();
        }
        this.images.add(image);
    }
    
    /**
     * Check if response has any images.
     * 
     * @return true if images list is not empty
     */
    public boolean hasImages() {
        return images != null && !images.isEmpty();
    }
    
    /**
     * Get count of images in response.
     * 
     * @return Number of images
     */
    public int getImageCount() {
        return images != null ? images.size() : 0;
    }
}
