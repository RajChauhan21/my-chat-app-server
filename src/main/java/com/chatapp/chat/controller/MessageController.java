package com.chatapp.chat.controller;

import com.chatapp.chat.DTO.*;
import com.chatapp.chat.service.GroupService;
import com.chatapp.chat.service.PrivateChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class MessageController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private PrivateChatService privateChatService;

    @Autowired
    private GroupService groupService;

    @MessageMapping("/user")
    @SendTo("/topic/get")
    public ChatMessage sendMessage(ChatMessage message) {
        return message;
    }

    @MessageMapping("/friend/add")
    public void addFriend(AddOrRemoveFriend addOrRemoveFriend) {
        Object response = privateChatService.addFriend(addOrRemoveFriend);
        if (response != "1") {
            messagingTemplate.convertAndSend("/topic/add-leave/friend/" + addOrRemoveFriend.getFriendName(), addOrRemoveFriend);
            messagingTemplate.convertAndSend("/topic/add-leave/friend/" + addOrRemoveFriend.getSelfName(), addOrRemoveFriend);
//            messagingTemplate.convertAndSend("/topic/add-leave/friend", addOrRemoveFriend);
            System.out.println("new friend added");
        }
    }

    @MessageMapping("/friend/remove")
    public void removeFriend(AddOrRemoveFriend addOrRemoveFriend) {
        AddOrRemoveFriend response = privateChatService.removeFriend(addOrRemoveFriend);

        messagingTemplate.convertAndSend("/topic/add-leave/friend/" + addOrRemoveFriend.getFriendName(), response);
        messagingTemplate.convertAndSend("/topic/add-leave/friend/" + addOrRemoveFriend.getSelfName(), response);
        System.out.println("friend removed: " + addOrRemoveFriend.getFriendName()+ " by " +addOrRemoveFriend.getSelfName());

    }

    @MessageMapping("/chat/test")
    public void sendOneToOneMessage(ChatMessage chatMessage) {
        chatMessage.setTimestamp(LocalDateTime.now());
        privateChatService.sendOneToOneMessage(chatMessage);
        messagingTemplate.convertAndSend("/topic/send/" + chatMessage.getReceiver(), chatMessage);
        System.out.println("✅ Message sent to user: " + chatMessage.getReceiver());
        System.out.println("📝 Message content: " + chatMessage.getContent());
    }

    @MessageMapping("/create/group")
    public void addGroup(Group group) {
        Group group1 = groupService.createGroup(group);

        messagingTemplate.convertAndSend("/topic/user/" + group.getAdmin(), group1 != null ? group1 : "");

        if (group1 != null) System.out.println("Group created successfully");
        else System.out.println("Group already exists");
    }

    @MessageMapping("/leave/group/{groupId}/{leavingUser}")
    public void leaveGroup(@DestinationVariable String groupId,@DestinationVariable String leavingUser){
        System.out.println(leavingUser + "leaving from " + groupId);
       List<String> members = groupService.leaveGroup(groupId,leavingUser);
       messagingTemplate.convertAndSend("/topic/leave/group",new String[]{groupId,leavingUser});
    }

    @MessageMapping("/join/group")
    public void joinGroup(JoinRequest joinRequest) {
        Object response = groupService.joinGroup(joinRequest);
        if (response != null) {

            if (response != "2" && response != "1") {
                Group group = (Group) response;
                for (String members : group.getGroupMembers()) {
//                   messagingTemplate.convertAndSend("/topic/group/"+members, joinRequest.getMemberName()+ " joined the group");
                    messagingTemplate.convertAndSend("/topic/user/" + members, response);
                }

                System.out.println(joinRequest.getMemberName() + " joined the group");
            } else {
                messagingTemplate.convertAndSend("/topic/user/" + joinRequest.getMemberName(), response);
            }

            System.out.println(response);
        }

    }

    public void deleteGroup(String groupId) {
        groupService.deleteGroup(groupId);
    }

    @MessageMapping("/chat/group")
    public void sendGroupMessage(GroupChatMessage groupChatMessage) {

        List<String> groupMembers = groupService.sendMessageInGroup(groupChatMessage);
        if (groupMembers != null) {
            for (String members : groupMembers) {
                messagingTemplate.convertAndSend("/topic/group/" + members, groupChatMessage);
                System.out.println("messages sent to: " + members);
            }
        }
        System.out.println("group message arrived");
    }
}
