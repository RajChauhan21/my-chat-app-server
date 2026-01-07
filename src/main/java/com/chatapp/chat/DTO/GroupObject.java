package com.chatapp.chat.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
public class GroupObject { //this class holds the data retrieved from redis

    @JsonProperty("stream")
    private String stream;

    @JsonProperty("value")
    private Map<String, Object> value; // Generic map for flexibility

    @JsonProperty("id")
    private Map<String, Object> id; // Message ID info

    @JsonProperty("requiredStream")
    private String requiredStream;

//    // Optional: Redis metadata
//    private String redisStream;
//    private String messageId;
}
