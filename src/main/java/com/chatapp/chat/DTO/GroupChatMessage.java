package com.chatapp.chat.DTO;

import com.chatapp.chat.utils.MessageType;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class GroupChatMessage implements Serializable {

    private String content;
    private String group_name;
    private String sender;      // Set automatically from principal
    private String groupId;    // Must be provided by client
    private MessageType type;
    private List<String> groupMembers;
    private String timestamp;
}
