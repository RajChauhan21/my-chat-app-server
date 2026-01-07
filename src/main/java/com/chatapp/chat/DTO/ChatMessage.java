package com.chatapp.chat.DTO;

import com.chatapp.chat.utils.MessageType;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
public class ChatMessage implements Serializable {

//    private String sender;
//    private String content;
//    private String time;

    private String content;
    private String sender;      // Set automatically from principal
    private String receiver;    // Must be provided by client
    private MessageType type;
    private LocalDateTime timestamp; // Set automatically
}
