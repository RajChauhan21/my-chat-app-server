package com.chatapp.chat.service;

import com.chatapp.chat.DTO.AddOrRemoveFriend;
import com.chatapp.chat.DTO.ChatMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PrivateChatService {

    private final HashSet<String> privateChatKeys = new HashSet<>();

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.chat.ttl.mins}")
    private int expireMins;

    private final String SINGLE_CHAT = "private-chat";

    public Object addFriend(AddOrRemoveFriend addOrRemoveFriend){
        String streamName = SINGLE_CHAT+ addOrRemoveFriend.getSelfName().toLowerCase()+"-"+ addOrRemoveFriend.getFriendName().toLowerCase().trim();

        String reverseKey = SINGLE_CHAT+ addOrRemoveFriend.getFriendName().toLowerCase()+"-"+ addOrRemoveFriend.getSelfName().toLowerCase().trim();

        boolean alreadyPresent = redisTemplate.hasKey(streamName) || redisTemplate.hasKey(reverseKey);

//        if (alreadyPresent){
//            System.out.println("friend is already present");
//            return "1";
//        }
        privateChatKeys.add(streamName);

        Map<String,Object> initialMessage = new HashMap<>();
        initialMessage.put("type","private");
        initialMessage.put("friendName", addOrRemoveFriend.getFriendName());
        initialMessage.put("selfName", addOrRemoveFriend.getSelfName());
        initialMessage.put("message","Friend " + addOrRemoveFriend.getFriendName() + " added");
        initialMessage.put("createdAt", Instant.now().toString());
        if (!alreadyPresent) {
            redisTemplate.opsForStream().add(streamName, initialMessage);
            redisTemplate.expire(streamName,expireMins, TimeUnit.MINUTES);
        }
        return addOrRemoveFriend;
    }

    public AddOrRemoveFriend removeFriend(AddOrRemoveFriend addOrRemoveFriend){
        String streamName = SINGLE_CHAT+ addOrRemoveFriend.getSelfName().toLowerCase()+"-"+ addOrRemoveFriend.getFriendName().toLowerCase().trim();

        String reverseKey = SINGLE_CHAT+ addOrRemoveFriend.getFriendName().toLowerCase()+"-"+ addOrRemoveFriend.getSelfName().toLowerCase().trim();

        boolean streamKeyPresent = redisTemplate.hasKey(streamName);
        boolean reverseKeyPresent = redisTemplate.hasKey(reverseKey);

        try {
            if (streamKeyPresent) redisTemplate.delete(streamName);
            else if(reverseKeyPresent) redisTemplate.delete(reverseKey);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return addOrRemoveFriend;
    }

    public void sendOneToOneMessage(ChatMessage message){
        String streamName = SINGLE_CHAT+message.getSender().toLowerCase()+"-"+message.getReceiver().toLowerCase().trim();

        String reverseKey = SINGLE_CHAT+message.getReceiver().toLowerCase()+"-"+message.getSender().toLowerCase().trim();

//         if (!redisTemplate.hasKey(streamName) && !redisTemplate.hasKey(reverseKey)){
//            privateChatKeys.add(streamName.trim()); //return
//        }

        if (redisTemplate.hasKey(reverseKey.trim())){
//            Optional<String> value = privateChatKeys.stream().filter(str->str.equals(reverseKey)).findFirst();
            streamName = reverseKey.trim();
        }

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("type", "private");
        messageData.put("sender", message.getSender());
        messageData.put("receiver", message.getReceiver());
        messageData.put("message", message);
        messageData.put("timestamp", Instant.now().toString());
        messageData.put("id", UUID.randomUUID().toString());

        redisTemplate.opsForStream().add(streamName,messageData);
    }

    public List<Map<String, Object>> getChatMessages(String streamKey) {
        // Get all records from stream
        List<MapRecord<String, Object, Object>> records =
                redisTemplate.opsForStream().range(streamKey.trim(), Range.closed("-", "+"));

        // Transform to get only message objects
        return records.stream()
                .map(this::extractMessageFromRecord)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Map<String, Object> extractMessageFromRecord(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();

        // Get the "message" field from the record value
        Object messageObj = value.get("message");

        if (messageObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> messageMap = (Map<String, Object>) messageObj;

            // Optionally add message ID from record
            messageMap.put("redisMessageId", record.getId().getValue());

            return messageMap;
        }

        return null;
    }

    public List<Map<String, Object>> getMessages(String streamKey) {
        List<MapRecord<String, Object, Object>> records =
                redisTemplate.opsForStream().range(streamKey, Range.unbounded());

        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> messages = new ArrayList<>();

        for (MapRecord<String, Object, Object> record : records) {
            try {
                // Get the record value as Map
                Map<Object, Object> recordValue = record.getValue();

                // Convert the entire record value to JSON and back to properly parse
                String json = objectMapper.writeValueAsString(recordValue);
                Map<String, Object> parsedValue = objectMapper.readValue(
                        json, new TypeReference<Map<String, Object>>() {}
                );

                // Get the message field
                Object messageObj = parsedValue.get("message");

                if (messageObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageMap = (Map<String, Object>) messageObj;

                    // Add Redis ID if needed
                    messageMap.put("redisMessageId", record.getId().getValue());
                    messages.add(messageMap);
                }

                return Collections.singletonList(parsedValue);

            } catch (Exception e) {
                System.err.println("Error processing record " + record.getId() + ": " + e.getMessage());
                // Continue with next record
            }
        }

        return messages;
    }
}
