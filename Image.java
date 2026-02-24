package com.uhg.comm.edelivery.reportingservice.model;

import lombok.Data;

/**
 * Model class representing an image in OpenAI responses.
 * Used when Azure Search returns image citations in the response.
 * 
 * @author Your Team
 * @version 1.0
 */
@Data
public class Image {
    
    /**
     * URL of the image.
     * Example: https://storage.blob.core.windows.net/container/image.png
     */
    private String url;
    
    /**
     * Title or description of the image.
     * Optional field that provides context about the image.
     */
    private String title;
    
    /**
     * Alternative text for the image.
     * Used for accessibility purposes.
     */
    private String altText;
    
    /**
     * Source of the image (document name, filepath, etc.)
     */
    private String source;
    
    /**
     * Default constructor.
     */
    public Image() {
    }
    
    /**
     * Constructor with URL.
     * 
     * @param url The image URL
     */
    public Image(String url) {
        this.url = url;
    }
    
    /**
     * Constructor with URL and title.
     * 
     * @param url The image URL
     * @param title The image title
     */
    public Image(String url, String title) {
        this.url = url;
        this.title = title;
    }
}
