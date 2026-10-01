package com.buckshot.devtest;

import com.buckshot.ws.packet.c2s.TestPacket;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @PostMapping("/api/test")
    public com.buckshot.ws.packet.s2c.TestPacket test(@RequestBody TestPacket request) {
        return new com.buckshot.ws.packet.s2c.TestPacket("echo: " + request.message(), System.currentTimeMillis());
    }
}
