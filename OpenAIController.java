package com.uhg.comm.edelivery.reportingservice.controller;
 
import com.fasterxml.jackson.core.JsonProcessingException;
import com.uhg.comm.edelivery.reportingservice.bean.MessageWithImageResponse;
import com.uhg.comm.edelivery.reportingservice.model.Message;
import com.uhg.comm.edelivery.reportingservice.service.OpenAIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class OpenAIController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAIController.class);

    @Autowired
    private OpenAIService openAIService;
    
    private final Map<String, ConversationSession> sessionStore = new ConcurrentHashMap<>();
    private final LinkedList<GlobalConversationContext> globalRecentConversations = new LinkedList<>();
    private final int MAX_GLOBAL_RECENT = 200;
    
    private static final String SESSION_COOKIE_NAME = "OPENAI_SESSION_ID";
    private static final int MAX_MESSAGES_PER_SESSION = 50;
    private static final long SESSION_TIMEOUT_MS = 48 * 60 * 60 * 1000;
    private static final long GLOBAL_RECENT_TIMEOUT_MS = 15 * 60 * 1000;
    private static final Pattern PRINTRAIL_PATTERN = Pattern.compile("\\bPT\\d{4}\\b", Pattern.CASE_INSENSITIVE);

    private static class ConversationSession {
        private final List<Message> messages;
        private long lastAccessTime;
        private final List<String> printrailHistory;
        
        public ConversationSession() {
            this.messages = new ArrayList<>();
            this.printrailHistory = new ArrayList<>();
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        public void addMessage(Message message) {
            messages.add(message);
            updateAccessTime();
            
            Matcher matcher = PRINTRAIL_PATTERN.matcher(message.getContent());
            while (matcher.find()) {
                String printrail = matcher.group();
                printrailHistory.remove(printrail);
                printrailHistory.add(0, printrail);
                LOGGER.debug("Tracked printrail: {} (total tracked: {})", printrail, printrailHistory.size());
            }
            
            if (messages.size() > MAX_MESSAGES_PER_SESSION) {
                messages.subList(0, messages.size() - MAX_MESSAGES_PER_SESSION).clear();
            }
        }
        
        public List<Message> getMessages() {
            updateAccessTime();
            return new ArrayList<>(messages);
        }
        
        public void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - lastAccessTime > SESSION_TIMEOUT_MS;
        }
        
        public int size() {
            return messages.size();
        }
        
        public String getMostRecentPrintrail() {
            return printrailHistory.isEmpty() ? null : printrailHistory.get(0);
        }
        
        public List<String> getAllPrintrails() {
            return new ArrayList<>(printrailHistory);
        }
    }
    
    private static class GlobalConversationContext {
        private final String queryType;
        private final List<String> printrails;
        private final long timestamp;
        private final List<Message> messages;
        private final String sessionId;
        
        public GlobalConversationContext(String queryType, List<String> printrails, 
                                        List<Message> messages, String sessionId) {
            this.queryType = queryType;
            this.printrails = new ArrayList<>(printrails);
            this.timestamp = System.currentTimeMillis();
            this.messages = new ArrayList<>(messages);
            this.sessionId = sessionId;
        }
        
        public boolean isRecent() {
            return System.currentTimeMillis() - timestamp < GLOBAL_RECENT_TIMEOUT_MS;
        }
        
        public boolean matches(String queryType, String excludeSessionId) {
            if (this.sessionId.equals(excludeSessionId)) {
                return false;
            }
            return this.queryType.equalsIgnoreCase(queryType) && !printrails.isEmpty();
        }
        
        public String getMostRecentPrintrail() {
            return printrails.isEmpty() ? null : printrails.get(0);
        }
    }

    @PostMapping("/OpenAI")
    public ResponseEntity<MessageWithImageResponse> OpenAI(
            @RequestBody List<Message> messageList, 
            @RequestHeader("queryType") String queryType,
            @RequestParam(required = false) String sessionId,
            HttpServletRequest request,
            HttpServletResponse response) throws JsonProcessingException {
        
        LOGGER.info("==========================================================");
        LOGGER.info("=== OpenAI Controller Request ===");
        LOGGER.info("Query Type: {}", queryType);
        LOGGER.info("Frontend sent {} messages", messageList != null ? messageList.size() : 0);
        
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = getSessionIdFromCookie(request);
            if (sessionId == null) {
                sessionId = UUID.randomUUID().toString();
                LOGGER.info("🆕 Generated NEW session ID: {}", sessionId);
            } else {
                LOGGER.info("🔄 Using EXISTING sessionId from cookie: {}", sessionId);
            }
            setSessionCookie(response, sessionId);
        } else {
            LOGGER.info("📌 Using sessionId from parameter: {}", sessionId);
        }
        
        ConversationSession session = sessionStore.computeIfAbsent(sessionId, k -> {
            LOGGER.info("🎯 Created NEW conversation session: {}", k);
            return new ConversationSession();
        });
        
        LOGGER.info("📊 Session {} statistics:", sessionId);
        LOGGER.info("   - Stored messages: {}", session.size());
        LOGGER.info("   - Tracked printrails: {}", session.getAllPrintrails());
        LOGGER.info("   - Most recent printrail: {}", session.getMostRecentPrintrail());
        
        if (messageList != null && !messageList.isEmpty()) {
            for (int i = 0; i < Math.min(messageList.size(), 3); i++) {
                Message msg = messageList.get(i);
                String preview = msg.getContent().substring(0, Math.min(100, msg.getContent().length()));
                LOGGER.info("   - Frontend message[{}] ({}): {}...", i, msg.getRole(), preview);
            }
        }
        
        boolean frontendSendsFullHistory = isFrontendSendingFullHistory(messageList, session);
        
        List<Message> fullConversationHistory;
        
        if (frontendSendsFullHistory) {
            LOGGER.info("✅ Frontend IS maintaining conversation history");
            fullConversationHistory = messageList;
            session.messages.clear();
            for (Message msg : messageList) {
                session.addMessage(msg);
            }
        } else {
            LOGGER.warn("⚠️ Frontend sent only {} messages - using SERVER-SIDE history", messageList.size());
            
            if (session.size() == 0) {
                LOGGER.info("🔍 Session empty - searching global recent conversations...");
                GlobalConversationContext match = findBestGlobalMatch(queryType, sessionId);
                if (match != null) {
                    LOGGER.info("🎯 FOUND MATCH! Using conversation with printrail: {}", match.getMostRecentPrintrail());
                    for (Message msg : match.messages) {
                        session.addMessage(msg);
                    }
                } else {
                    LOGGER.warn("❌ No matching global conversation found");
                }
            }
            
            for (Message msg : messageList) {
                if ("user".equals(msg.getRole())) {
                    session.addMessage(msg);
                    String preview = msg.getContent().substring(0, Math.min(100, msg.getContent().length()));
                    LOGGER.info("➕ Added user message to session: {}...", preview);
                }
            }
            
            fullConversationHistory = session.getMessages();
        }
        
        LOGGER.info("📝 FINAL conversation size: {} messages", fullConversationHistory.size());
        LOGGER.info("📝 Current printrails in session: {}", session.getAllPrintrails());
        
        if (!fullConversationHistory.isEmpty()) {
            StringBuilder sequence = new StringBuilder();
            for (int i = 0; i < fullConversationHistory.size(); i++) {
                if (i > 0) sequence.append(" → ");
                sequence.append(fullConversationHistory.get(i).getRole());
            }
            LOGGER.info("📝 Message Sequence: {}", sequence);
            
            for (int i = fullConversationHistory.size() - 1; i >= 0; i--) {
                if ("user".equals(fullConversationHistory.get(i).getRole())) {
                    String content = fullConversationHistory.get(i).getContent();
                    LOGGER.info("📝 Last user question: {}", content.substring(0, Math.min(150, content.length())));
                    break;
                }
            }
        }
        
        try {
            LOGGER.info("🚀 Calling OpenAI service with {} messages...", fullConversationHistory.size());
            MessageWithImageResponse result = openAIService.callOpenAIApi(fullConversationHistory, queryType);
            
            if (result != null && result.getMessage() != null) {
                LOGGER.info("✅ OpenAI service call SUCCESSFUL");
                
                session.addMessage(result.getMessage());
                LOGGER.info("💾 Stored assistant response. Session now has {} messages", session.size());
                LOGGER.info("💾 Session now tracking printrails: {}", session.getAllPrintrails());
                
                storeGlobalConversation(queryType, session.getAllPrintrails(), 
                                       session.getMessages(), sessionId);
                
                cleanupExpiredSessions();
                cleanupOldGlobalConversations();
                
                LOGGER.info("==========================================================");
                
                return ResponseEntity.ok()
                    .header("X-Session-Id", sessionId)
                    .header("X-Tracked-Printrails", String.join(",", session.getAllPrintrails()))
                    .body(result);
            } else {
                LOGGER.error("❌ OpenAI service returned NULL result");
                LOGGER.info("==========================================================");
                return ResponseEntity.internalServerError().build();
            }
        } catch (Exception e) {
            LOGGER.error("❌ ERROR calling OpenAI service", e);
            LOGGER.info("==========================================================");
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private synchronized GlobalConversationContext findBestGlobalMatch(String queryType, String excludeSessionId) {
        GlobalConversationContext bestMatch = null;
        long mostRecent = 0;
        
        for (GlobalConversationContext context : globalRecentConversations) {
            if (context.isRecent() && context.matches(queryType, excludeSessionId)) {
                if (context.timestamp > mostRecent) {
                    bestMatch = context;
                    mostRecent = context.timestamp;
                }
            }
        }
        
        if (bestMatch != null) {
            LOGGER.info("Found global match with printrail {} from {} seconds ago", 
                bestMatch.getMostRecentPrintrail(), 
                (System.currentTimeMillis() - bestMatch.timestamp) / 1000);
        }
        
        return bestMatch;
    }
    
    private synchronized void storeGlobalConversation(String queryType, List<String> printrails, 
                                                     List<Message> messages, String sessionId) {
        if (!printrails.isEmpty()) {
            GlobalConversationContext context = new GlobalConversationContext(
                queryType, printrails, messages, sessionId);
            globalRecentConversations.addFirst(context);
            
            while (globalRecentConversations.size() > MAX_GLOBAL_RECENT) {
                globalRecentConversations.removeLast();
            }
            
            LOGGER.debug("Stored global conversation with printrails: {}", printrails);
        }
    }
    
    private synchronized void cleanupOldGlobalConversations() {
        if (Math.random() > 0.05) return;
        
        int before = globalRecentConversations.size();
        globalRecentConversations.removeIf(context -> !context.isRecent());
        int removed = before - globalRecentConversations.size();
        
        if (removed > 0) {
            LOGGER.info("Cleaned up {} old global conversations, {} remaining", removed, globalRecentConversations.size());
        }
    }
    
    private String getSessionIdFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
    
    private void setSessionCookie(HttpServletResponse response, String sessionId) {
        Cookie cookie = new Cookie(SESSION_COOKIE_NAME, sessionId);
        cookie.setMaxAge(48 * 60 * 60);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }
    
    private boolean isFrontendSendingFullHistory(List<Message> messageList, ConversationSession session) {
        if (messageList == null || messageList.isEmpty()) return false;
        if (session.size() == 0) return true;
        if (messageList.size() == 1 && session.size() > 1) return false;
        
        boolean hasAssistantMessage = messageList.stream()
            .anyMatch(msg -> "assistant".equals(msg.getRole()));
        
        return hasAssistantMessage || messageList.size() >= session.size();
    }
    
    private void cleanupExpiredSessions() {
        if (Math.random() > 0.1) return;
        
        long before = sessionStore.size();
        sessionStore.entrySet().removeIf(entry -> entry.getValue().isExpired());
        long removed = before - sessionStore.size();
        
        if (removed > 0) {
            LOGGER.info("Session cleanup: {} expired, {} remaining", removed, sessionStore.size());
        }
    }
    
    @DeleteMapping("/OpenAI/session/{sessionId}")
    public ResponseEntity<String> clearSession(@PathVariable String sessionId) {
        ConversationSession session = sessionStore.remove(sessionId);
        if (session != null) {
            LOGGER.info("Cleared session: {}", sessionId);
            return ResponseEntity.ok("Session cleared");
        }
        return ResponseEntity.notFound().build();
    }
    
    @GetMapping("/OpenAI/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionId) {
        ConversationSession session = sessionStore.get(sessionId);
        if (session != null) {
            Map<String, Object> info = new HashMap<>();
            info.put("sessionId", sessionId);
            info.put("messageCount", session.size());
            info.put("printrails", session.getAllPrintrails());
            info.put("mostRecentPrintrail", session.getMostRecentPrintrail());
            info.put("lastAccessTime", session.lastAccessTime);
            return ResponseEntity.ok(info);
        }
        return ResponseEntity.notFound().build();
    }
    
    @PostMapping("/OpenAI/cleanup")
    public ResponseEntity<String> manualCleanup() {
        long sessionsBefore = sessionStore.size();
        sessionStore.entrySet().removeIf(entry -> entry.getValue().isExpired());
        long sessionsRemoved = sessionsBefore - sessionStore.size();
        
        int globalBefore = globalRecentConversations.size();
        globalRecentConversations.removeIf(context -> !context.isRecent());
        int globalRemoved = globalBefore - globalRecentConversations.size();
        
        String message = String.format(
            "Cleanup: %d sessions removed (%d remaining), %d global conversations removed (%d remaining)", 
            sessionsRemoved, sessionStore.size(), globalRemoved, globalRecentConversations.size());
        
        LOGGER.info(message);
        return ResponseEntity.ok(message);
    }
}
