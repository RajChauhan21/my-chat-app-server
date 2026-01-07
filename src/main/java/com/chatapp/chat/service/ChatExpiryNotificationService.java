package com.chatapp.chat.service;

import com.chatapp.chat.DTO.GroupObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@EnableScheduling
public class ChatExpiryNotificationService {
//15 * 60 * 1000
    private static final long CHECK_INTERVAL_MS = 15 * 60 * 1000; // 15 minutes
    private static final long NOTIFICATION_WINDOW_SECONDS = 5 * 60; // 5 minutes
    // Store already notified keys (expire after 30 minutes)
    private final Map<String, Long> notifiedKeys = new ConcurrentHashMap<>();

    Logger log = LoggerFactory.getLogger(ChatExpiryNotificationService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private GroupService groupService;

    @Autowired
    private PrivateChatService privateChatService;

    //runs every 15 mins
    @Scheduled(fixedDelay = CHECK_INTERVAL_MS)
    public void checkChatExpirations() {
        try {
            Set<String> allKeys = redisTemplate.keys("*");

            if (allKeys == null || allKeys.isEmpty()) {
                System.out.println("Keys are null");
                return;
            }

            int notifiedCount = 0;

            for (String key : allKeys) {
                // Check if this is a chat stream key
                Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);

                if (ttlSeconds != null && ttlSeconds > 0) {
                    // Check if expiring within 5 minutes and not recently notified
                    if (ttlSeconds <= NOTIFICATION_WINDOW_SECONDS &&
                            !isRecentlyNotified(key)) {

                        // Send notification
                        sendChatExpiryNotification(key, ttlSeconds);
                        notifiedKeys.put(key, System.currentTimeMillis());
                        notifiedCount++;
                    }
                }

            }
            System.out.println("expiry scheduler called");
        } catch (Exception e) {
            System.out.println("Error running scheduler");
        }
    }

    public boolean isRecentlyNotified(String key) {
        Long notificationTime = notifiedKeys.get(key);
        if (notificationTime == null) return false;

        long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);
        return notificationTime > fiveMinutesAgo;
    }

    public void sendChatExpiryNotification(String streamKey, long ttlSeconds) {
        try {

            // Prepare notification
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "CHAT_EXPIRY_WARNING");
            notification.put("streamKey", streamKey);
            notification.put("ttlSeconds", ttlSeconds);
            notification.put("minutesRemaining", ttlSeconds / 60);
            notification.put("notificationTime", new Date());

            //for groups
            if (streamKey != null && streamKey.startsWith("group:chat:")) {

                ResponseEntity<GroupObject> groupObject =  groupService.getGroupById(streamKey);
                GroupObject object = groupObject.getBody();

                // Determine who to notify
                List<String> participants = (List<String>) object.getValue().get("members");
                // Notify each participant
                for (String participant : participants) {
                    messagingTemplate.convertAndSend("/topic/chat/expiry/"+participant, notification);
                }
            }
            //for personal chats
            else{
                List<Map<String, Object>> chatMessages = privateChatService.getMessages(streamKey);

                if (chatMessages!=null){
                    String ownerName = chatMessages.get(0).get("selfName").toString();
                    String friendName = chatMessages.get(0).get("friendName").toString();
                    messagingTemplate.convertAndSend("/topic/chat/expiry/"+ownerName, notification);
                    messagingTemplate.convertAndSend("/topic/chat/expiry/"+friendName, notification);
                }
            }

            log.info("Sent expiry notification for chat: {}", streamKey);

        } catch (Exception e) {
            log.error("Error sending expiry notification for key {}: {}", streamKey, e.getMessage());
        }
    }

    private void cleanupOldNotifications() {
        long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);
        notifiedKeys.entrySet().removeIf(entry -> entry.getValue() < fiveMinutesAgo);
    }
}
