package com.chatapp.chat.controller;

import com.chatapp.chat.DTO.GroupObject;
import com.chatapp.chat.service.GroupService;
import com.chatapp.chat.service.PrivateChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("private/chat")
public class PrivateChatController {

    @Autowired
    private PrivateChatService privateChatService;

    @Autowired
    private GroupService groupService;

    @GetMapping("get/{key}")
    public ResponseEntity<?> getPrivateMessage(@PathVariable String key){
        List<Map<String, Object>> chatMessages = privateChatService.getMessages(key);

        return new ResponseEntity<>(chatMessages, HttpStatus.ACCEPTED);
    }

    @GetMapping("group/{key}")
    public ResponseEntity<?> getGroupMessage(@PathVariable String key){
        ResponseEntity<?> chatMessages = groupService.getGroupById(key);

        return new ResponseEntity<>(chatMessages, HttpStatus.ACCEPTED);
    }
}
