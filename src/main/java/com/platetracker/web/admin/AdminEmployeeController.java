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
import com.platetracker.domain.Employee;
import com.platetracker.repository.DepartmentRepository;
import com.platetracker.repository.EmployeeRepository;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin/employees")
public class AdminEmployeeController {

    private final EmployeeRepository repo;
    private final DepartmentRepository departments;

    public AdminEmployeeController(EmployeeRepository repo, DepartmentRepository departments) {
        this.repo = repo;
        this.departments = departments;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long edit, Model model) {
        Employee form = edit == null ? null : repo.findById(edit).orElse(null);
        if (form == null) {
            form = new Employee();
            form.setDepartment(new Department());
        }
        model.addAttribute("form", form);
        return populate(model);
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") Employee form, BindingResult result,
                       Model model, RedirectAttributes redirect) {
        if (form.getDepartment() == null || form.getDepartment().getId() == null) {
            result.rejectValue("department", "required", "Choose a department.");
        }
        if (!result.hasErrors()) {
            repo.save(form);
            redirect.addFlashAttribute("msg", "Employee saved.");
            return "redirect:/admin/employees";
        }
        return populate(model);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            repo.deleteById(id);
            redirect.addFlashAttribute("msg", "Employee deleted.");
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error",
                    "That employee appears in the log or has a login, so it can't be deleted. Untick Active instead.");
        }
        return "redirect:/admin/employees";
    }

    private String populate(Model model) {
        model.addAttribute("items", repo.findAll(Sort.by("name")));
        model.addAttribute("departments", departments.findAll(Sort.by("name")));
        return "admin/employees";
    }
}
