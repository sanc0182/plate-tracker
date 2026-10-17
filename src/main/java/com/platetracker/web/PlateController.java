package com.platetracker.web;

import java.time.format.DateTimeFormatter;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.platetracker.domain.AppUser;
import com.platetracker.domain.Department;
import com.platetracker.domain.PlateLoan;
import com.platetracker.domain.Role;
import com.platetracker.repository.AppUserRepository;
import com.platetracker.repository.EmployeeRepository;
import com.platetracker.repository.PlateLoanRepository;
import com.platetracker.repository.PlateRepository;
import com.platetracker.service.BusinessRuleException;
import com.platetracker.service.PlateLoanService;

/** The employee-facing screen: take a plate, return a plate. */
@Controller
@RequestMapping("/plates")
public class PlateController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final PlateRepository plates;
    private final EmployeeRepository employees;
    private final PlateLoanRepository loans;
    private final AppUserRepository users;
    private final PlateLoanService service;

    public PlateController(PlateRepository plates, EmployeeRepository employees, PlateLoanRepository loans,
                           AppUserRepository users, PlateLoanService service) {
        this.plates = plates;
        this.employees = employees;
        this.loans = loans;
        this.users = users;
        this.service = service;
    }

    @GetMapping
    public String index(Authentication auth, Model model) {
        AppUser user = currentUser(auth);
        model.addAttribute("availablePlates", plates.findAvailable());
        model.addAttribute("openLoans", loans.findByTimeInIsNullOrderByTimeOutDesc());
        model.addAttribute("employees", employees.findByActiveTrueOrderByNameAsc());
        model.addAttribute("approvers", employees.findByActiveTrueAndDepartmentNameOrderByNameAsc(Department.RECEPTION));
        model.addAttribute("currentEmployeeId", user.getEmployee() == null ? null : user.getEmployee().getId());
        return "plates/index";
    }

    @PostMapping("/take")
    public String take(@RequestParam Long plateId,
                       @RequestParam(required = false) Long employeeId,
                       @RequestParam Long approverId,
                       @RequestParam String stockNumber,
                       Authentication auth, RedirectAttributes redirect) {
        AppUser user = currentUser(auth);
        // A login tied to an employee can only borrow for that employee, whatever the form says.
        Long effectiveEmployeeId = user.getEmployee() != null ? user.getEmployee().getId() : employeeId;
        try {
            PlateLoan loan = service.takePlate(plateId, effectiveEmployeeId, approverId, stockNumber);
            redirect.addFlashAttribute("msg", "Plate " + loan.getPlate().getPlateNumber()
                    + " taken at " + loan.getTimeOut().format(TIME) + ". Remember to return it.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/plates";
    }

    @PostMapping("/{loanId}/return")
    public String returnPlate(@PathVariable Long loanId, Authentication auth, RedirectAttributes redirect) {
        AppUser user = currentUser(auth);
        Long actingEmployeeId = (user.getRole() == Role.ADMIN || user.getEmployee() == null)
                ? null : user.getEmployee().getId();
        try {
            PlateLoan loan = service.returnPlate(loanId, actingEmployeeId);
            redirect.addFlashAttribute("msg", "Plate " + loan.getPlate().getPlateNumber()
                    + " returned at " + loan.getTimeIn().format(TIME) + ".");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/plates";
    }

    private AppUser currentUser(Authentication auth) {
        return users.findByUsername(auth.getName()).orElseThrow();
    }
}
