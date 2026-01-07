package com.chatapp.chat.service;

import com.chatapp.chat.DTO.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final Map<String, Group> groups = new ConcurrentHashMap<>();

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.chat.ttl.mins}")
    private int expireMins;

    private final String GROUP_PREFIX = "group:chat:";

//    @Autowired
//    private ChatExpiryNotificationService notificationService;

    public List<String> sendMessageInGroup(GroupChatMessage groupChatMessage){

        String streamName = GROUP_PREFIX+groupChatMessage.getGroup_name().toLowerCase().trim();

        //check if group exists or not
//        if(redisTemplate.hasKey(streamName)){
//            return null;
//        }

        ResponseEntity<GroupObject> groupObject = getGroupById(streamName);
        GroupObject object = groupObject.getBody();

        List<String> members = (List<String>) object.getValue().get("members");

        Map<String, Object> content = new HashMap<>();
        content.put("type","chat");
        content.put("groupId", groupChatMessage.getGroup_name());
        content.put("message",groupChatMessage.getContent());
        content.put("members", members);
        content.put("admin", object.getValue().get("admin"));
        content.put("createdAt", Instant.now().toString());


        redisTemplate.opsForStream().add(streamName, content);

        return members;
    }

    public Group createGroup(Group group){
        groups.put(group.getId(), group);
        String streamName = GROUP_PREFIX+group.getName().toLowerCase().trim();
        if(redisTemplate.hasKey(streamName)){
            return null;
        }

        Map<String,Object> initialMessage = new HashMap<>();
        initialMessage.put("type","group");
        initialMessage.put("groupId",group.getName().toLowerCase().trim());
        initialMessage.put("admin",group.getAdmin());
        initialMessage.put("members",group.getGroupMembers());
        initialMessage.put("message","Group " + group.getName().toLowerCase().trim() + " created");
        initialMessage.put("createdAt", Instant.now().toString());

        redisTemplate.opsForStream().add(streamName,initialMessage);
        redisTemplate.expire(streamName,expireMins, TimeUnit.MINUTES);
        group.setId(streamName);
        group.setType("create");
        return group;
    }

    public List<String> leaveGroup(String groupId, String leavingUser){
        Boolean groupExists = redisTemplate.hasKey(groupId);
        if (!groupExists){
            System.out.println("Group not found");
            return new ArrayList<>();
        }
        GroupObject object = getGroupById(groupId).getBody();
        List<String> members = (List<String>) object.getValue().get("members");
        boolean removed = members.remove(leavingUser.toLowerCase().trim());
        if (removed) System.out.println(leavingUser + " Leaved successfully");

        if (members.isEmpty()){
            redisTemplate.delete(groupId);
            return members;
        }
        List<String> list = new ArrayList<>(members);
        Map<String, Object> updatedObject = new HashMap<>(object.getValue());
        updatedObject.put("members",list);
       redisTemplate.opsForStream().add(groupId,updatedObject);
       return list;
    }

    public Object joinGroup(JoinRequest joinRequest){

       Boolean groupExists = redisTemplate.hasKey(GROUP_PREFIX+joinRequest.getGroupId().toLowerCase().trim());
        System.out.println("is group exists "+groupExists);
       //check if group exists or not
       if (!groupExists) return "1";

       ResponseEntity<GroupObject> groupObject = getGroupById(GROUP_PREFIX+joinRequest.getGroupId().toLowerCase().trim());
       GroupObject object = groupObject.getBody();

       if (object!=null) {
           //check if user is already present in the group
           List<String> members = (List<String>) object.getValue().get("members");
           boolean existingMember = members.stream().anyMatch(name -> name.toLowerCase().trim().equals(joinRequest.getMemberName().toLowerCase().trim()));
           System.out.println("is member present "+existingMember);
           if (existingMember){
               String fullValue = (String) object.getValue().get("groupId");
               String[] parts = fullValue.split(":");
               String groupName = parts[parts.length - 1];
               Group group = new Group();
               group.setGroupMembers(members);
               group.setName(groupName);
               group.setAdmin((String) object.getValue().get("admin"));
               group.setId(object.getStream());
               group.setType("join");
               group.setRequest(joinRequest);
               return group;
           }

           Group updatedGroup = new Group();
           updatedGroup.setId(object.getStream());

           //extract group name from redis stream
           String fullValue = (String) object.getValue().get("groupId");
           String[] parts = fullValue.split(":");
           String groupName = parts[parts.length - 1];

           updatedGroup.setName(groupName);
           updatedGroup.setAdmin((String) object.getValue().get("admin"));

           //add a new member
           List<String> list = new ArrayList<>(members);
           list.add(joinRequest.getMemberName());
           updatedGroup.setGroupMembers(list);

           //make a new copy of map/value to save in redis
           Map<String, Object> updatedObject = new HashMap<>(object.getValue());
           updatedObject.put("members",list);

           //save in redis
           redisTemplate.opsForStream().add(GROUP_PREFIX+joinRequest.getGroupId(),updatedObject);
           redisTemplate.expire(GROUP_PREFIX+joinRequest.getGroupId(),expireMins, TimeUnit.MINUTES);
//           Long ttlSeconds = redisTemplate.getExpire(GROUP_PREFIX+joinRequest.getGroupId(), TimeUnit.SECONDS);
//           if(notificationService.isRecentlyNotified(GROUP_PREFIX+joinRequest.getGroupId())){
//               notificationService.sendChatExpiryNotification(GROUP_PREFIX+joinRequest.getGroupId(),ttlSeconds);
//           }
           groups.put(joinRequest.getGroupId(),updatedGroup);
           updatedGroup.setType("join");
           updatedGroup.setRequest(joinRequest);
           return updatedGroup;
       }
       return null;
    }

    public List<Group> getGroupsForUser(String name){
        return groups.values().stream()
                .filter(group->group.getMembers().stream().anyMatch(member->member.getName().equals(name))
                ).collect(Collectors.toList());
    }

    public ResponseEntity<GroupObject> getGroupById(String id){
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().range(id.toLowerCase().trim(), Range.closed("-", "+"));

        if (records == null || records.isEmpty()) {
            return null;
        }
        // Convert first record to GroupDTO
        return new ResponseEntity<>(convertToStreamResponseDTO(records.get(records.size()-1)), HttpStatus.ACCEPTED);

    }

    public boolean isUserMemberOfGroup(String groupId, String memberName){
        Group group = groups.get(groupId.toLowerCase().trim());
        if (group==null) return false;

        return group.getMembers().stream().anyMatch(name->name.getName().equals(memberName.toLowerCase().trim()));
    }

    public void deleteGroup(String groupId){
        groups.remove(groupId.toLowerCase().trim());
    }

    private GroupObject convertToStreamResponseDTO(MapRecord<String, Object, Object> record) {
        try {
            // Convert record to Map
            Map<String, Object> result = new HashMap<>();
            result.put("stream", record.getStream());
            result.put("requiredStream", record.getStream());

            // Add ID info
            Map<String, Object> idInfo = new HashMap<>();
            idInfo.put("value", record.getId().getValue());
            idInfo.put("timestamp", record.getId().getTimestamp());
            idInfo.put("sequence", record.getId().getSequence());
            result.put("id", idInfo);

            // Add value (convert Map<Object, Object> to Map<String, Object>)
            Map<Object, Object> recordValue = record.getValue();
            Map<String, Object> valueMap = new HashMap<>();

            for (Map.Entry<Object, Object> entry : recordValue.entrySet()) {
                valueMap.put(entry.getKey().toString(), entry.getValue());
            }
            result.put("value", valueMap);

            // Convert to JSON then to DTO
            String json = objectMapper.writeValueAsString(result);
            return objectMapper.readValue(json, GroupObject.class);

        } catch (Exception e) {
            throw new RuntimeException("Error converting to StreamResponseDTO", e);
        }
    }

}
