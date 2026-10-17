package com.platetracker.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication auth) {
        return isAdmin(auth) ? "redirect:/admin/loans" : "redirect:/plates";
    }

    @GetMapping("/admin")
    public String admin() {
        return "redirect:/admin/loans";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
