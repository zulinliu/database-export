package com.dbexport.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/login.html")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/index.html")
    public String indexPage() {
        return "index";
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/index.html";
    }
}
