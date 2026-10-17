package com.platetracker.web.admin;

import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.platetracker.domain.AppUser;
import com.platetracker.domain.Role;
import com.platetracker.repository.AppUserRepository;
import com.platetracker.repository.EmployeeRepository;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final AppUserRepository repo;
    private final EmployeeRepository employees;
    private final PasswordEncoder encoder;

    public AdminUserController(AppUserRepository repo, EmployeeRepository employees, PasswordEncoder encoder) {
        this.repo = repo;
        this.employees = employees;
        this.encoder = encoder;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("form", new UserForm());
        return populate(model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") UserForm form, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        if (!result.hasErrors() && repo.existsByUsername(form.getUsername())) {
            result.rejectValue("username", "duplicate", "That username is taken.");
        }
        if (result.hasErrors()) {
            return populate(model);
        }
        AppUser user = new AppUser();
        user.setUsername(form.getUsername());
        user.setPasswordHash(encoder.encode(form.getPassword()));
        user.setRole(form.getRole());
        if (form.getEmployeeId() != null) {
            user.setEmployee(employees.findById(form.getEmployeeId()).orElse(null));
        }
        repo.save(user);
        redirect.addFlashAttribute("msg", "Login created.");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication auth, RedirectAttributes redirect) {
        AppUser target = repo.findById(id).orElse(null);
        if (target != null && target.getUsername().equals(auth.getName())) {
            redirect.addFlashAttribute("error", "You can't delete the login you are signed in with.");
        } else {
            repo.deleteById(id);
            redirect.addFlashAttribute("msg", "Login deleted.");
        }
        return "redirect:/admin/users";
    }

    private String populate(Model model) {
        model.addAttribute("items", repo.findAll(Sort.by("username")));
        model.addAttribute("employees", employees.findByActiveTrueOrderByNameAsc());
        model.addAttribute("roles", Role.values());
        return "admin/users";
    }
}
