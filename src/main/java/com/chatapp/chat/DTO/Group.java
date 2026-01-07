package com.chatapp.chat.DTO;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class Group implements Serializable {

    private String type;
    private String id;
    private String name;
    private List<GroupMember> members;
    private List<String> groupMembers;
    private String admin;
    private JoinRequest request;
}
