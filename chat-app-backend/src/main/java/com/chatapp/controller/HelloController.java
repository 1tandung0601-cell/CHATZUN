package com.chatapp.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
@RestController
public class HelloController {

    @GetMapping("/hello/{name}")
    public String helloPath(@PathVariable String name) {
        return "Hello " + name + "!";
    }

    @GetMapping("/hello")
    public String helloQuery(@RequestParam String name) {
        return "Hello " + name + "!";
    }
}