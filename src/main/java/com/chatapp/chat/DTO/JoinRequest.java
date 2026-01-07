package com.chatapp.chat.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JoinRequest {

    private String groupId;

    private String memberName;
}
