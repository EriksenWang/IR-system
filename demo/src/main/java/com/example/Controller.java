package com.example;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/search")
public class Controller {
    @GetMapping
    public String search() {

        return "hello world";
    }
    
}
