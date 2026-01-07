package com.chatapp.chat.DTO;

import lombok.Data;

import java.io.Serializable;

@Data
public class AddOrRemoveFriend implements Serializable {

    private String id;
    private String username;
    private String friendName;
    private String selfName;
    private String type; //add or remove
}
