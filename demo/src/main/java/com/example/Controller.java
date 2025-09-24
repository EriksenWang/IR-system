package com.example;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
public class Controller {
    @GetMapping
    public String search() {

        return "hello world";
    }
    
}
