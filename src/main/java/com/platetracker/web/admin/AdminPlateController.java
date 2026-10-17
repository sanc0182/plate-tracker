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

import com.platetracker.domain.Plate;
import com.platetracker.repository.PlateRepository;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin/plates")
public class AdminPlateController {

    private final PlateRepository repo;

    public AdminPlateController(PlateRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long edit, Model model) {
        model.addAttribute("form", edit == null ? new Plate() : repo.findById(edit).orElseGet(Plate::new));
        model.addAttribute("items", repo.findAll(Sort.by("plateNumber")));
        return "admin/plates";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") Plate form, BindingResult result,
                       Model model, RedirectAttributes redirect) {
        // Check up front instead of waiting for the database to reject the insert.
        if (!result.hasErrors() && form.getPlateNumber() != null) {
            boolean taken = form.getId() == null
                    ? repo.existsByPlateNumber(form.getPlateNumber())
                    : repo.existsByPlateNumberAndIdNot(form.getPlateNumber(), form.getId());
            if (taken) {
                result.rejectValue("plateNumber", "duplicate", "That plate number already exists.");
            }
        }
        if (!result.hasErrors()) {
            try {
                repo.save(form);
                redirect.addFlashAttribute("msg", "Plate saved.");
                return "redirect:/admin/plates";
            } catch (DataIntegrityViolationException e) {
                result.rejectValue("plateNumber", "duplicate", "That plate number already exists.");
            }
        }
        model.addAttribute("items", repo.findAll(Sort.by("plateNumber")));
        return "admin/plates";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            repo.deleteById(id);
            redirect.addFlashAttribute("msg", "Plate deleted.");
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error",
                    "That plate appears in the log, so it can't be deleted. Untick Active instead.");
        }
        return "redirect:/admin/plates";
    }
}
