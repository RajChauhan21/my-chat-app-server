package com.chatapp.chat.utils;

import java.io.Serializable;

public enum MessageType implements Serializable {
    CHAT,
    JOIN,
    LEAVE,
    TYPING,
    SENT,
    GROUP_CHAT,
    STOP_TYPING
}
