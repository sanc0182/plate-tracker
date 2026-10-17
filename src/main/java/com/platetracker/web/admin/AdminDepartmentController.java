package com.platetracker.web.admin;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.platetracker.domain.Department;
import com.platetracker.repository.DepartmentRepository;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin/departments")
public class AdminDepartmentController {

    private final DepartmentRepository repo;

    public AdminDepartmentController(DepartmentRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long edit, Model model) {
        model.addAttribute("form", edit == null ? new Department() : repo.findById(edit).orElseGet(Department::new));
        model.addAttribute("items", repo.findAll(Sort.by("name")));
        return "admin/departments";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") Department form, BindingResult result,
                       Model model, RedirectAttributes redirect) {
        if (!result.hasErrors() && form.getName() != null) {
            boolean taken = form.getId() == null
                    ? repo.existsByName(form.getName())
                    : repo.existsByNameAndIdNot(form.getName(), form.getId());
            if (taken) {
                result.rejectValue("name", "duplicate", "A department with that name already exists.");
            }
        }
        if (!result.hasErrors()) {
            try {
                repo.save(form);
                redirect.addFlashAttribute("msg", "Department saved.");
                return "redirect:/admin/departments";
            } catch (DataIntegrityViolationException e) {
                result.rejectValue("name", "duplicate", "A department with that name already exists.");
            }
        }
        model.addAttribute("items", repo.findAll(Sort.by("name")));
        return "admin/departments";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            repo.deleteById(id);
            redirect.addFlashAttribute("msg", "Department deleted.");
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", "That department still has employees and cannot be deleted.");
        }
        return "redirect:/admin/departments";
    }
}
