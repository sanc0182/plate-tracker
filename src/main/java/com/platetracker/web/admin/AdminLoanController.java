package com.platetracker.web.admin;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
import org.springframework.web.server.ResponseStatusException;

import com.platetracker.domain.Department;
import com.platetracker.domain.PlateLoan;
import com.platetracker.repository.EmployeeRepository;
import com.platetracker.repository.PlateLoanAuditRepository;
import com.platetracker.repository.PlateLoanRepository;
import com.platetracker.service.BusinessRuleException;
import com.platetracker.service.LoanCorrection;
import com.platetracker.service.PlateLoanService;

import jakarta.validation.Valid;

/** The digital version of the paper sheet, plus admin corrections with an audit trail. */
@Controller
@RequestMapping("/admin/loans")
public class AdminLoanController {

    private static final int PAGE_SIZE = 50;

    private final PlateLoanRepository loans;
    private final PlateLoanAuditRepository audits;
    private final EmployeeRepository employees;
    private final PlateLoanService service;

    public AdminLoanController(PlateLoanRepository loans, PlateLoanAuditRepository audits,
                               EmployeeRepository employees, PlateLoanService service) {
        this.loans = loans;
        this.audits = audits;
        this.employees = employees;
        this.service = service;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", loans.findAll(
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "timeOut"))));
        return "admin/loans";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        PlateLoan loan = find(id);
        model.addAttribute("form", LoanCorrectionForm.fromLoan(loan));
        return populateEdit(model, loan);
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id, @Valid @ModelAttribute("form") LoanCorrectionForm form,
                       BindingResult result, Authentication auth, Model model, RedirectAttributes redirect) {
        PlateLoan loan = find(id);
        if (!result.hasErrors()) {
            try {
                service.correctLoan(id, new LoanCorrection(form.getEmployeeId(), form.getApproverId(),
                        form.getStockNumber(), form.getTimeOut(), form.getTimeIn(), form.getReason()), auth.getName());
                redirect.addFlashAttribute("msg", "Log entry corrected. The change has been recorded.");
                return "redirect:/admin/loans";
            } catch (BusinessRuleException e) {
                result.reject("correction", e.getMessage());
            }
        }
        return populateEdit(model, loan);
    }

    private PlateLoan find(Long id) {
        return loans.findWithDetailsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private String populateEdit(Model model, PlateLoan loan) {
        model.addAttribute("loan", loan);
        model.addAttribute("employees", employees.findAll(Sort.by("name")));
        model.addAttribute("approvers", employees.findByDepartmentNameOrderByNameAsc(Department.RECEPTION));
        model.addAttribute("history", audits.findByLoanIdOrderByChangedAtDescIdDesc(loan.getId()));
        return "admin/loan-edit";
    }
}
