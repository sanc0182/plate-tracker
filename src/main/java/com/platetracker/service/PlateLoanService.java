package com.platetracker.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.platetracker.domain.Department;
import com.platetracker.domain.Employee;
import com.platetracker.domain.Plate;
import com.platetracker.domain.PlateLoan;
import com.platetracker.domain.PlateLoanAudit;
import com.platetracker.repository.EmployeeRepository;
import com.platetracker.repository.PlateLoanAuditRepository;
import com.platetracker.repository.PlateLoanRepository;
import com.platetracker.repository.PlateRepository;

/** The core of the app: taking a plate out, bringing it back, and (admin only) correcting mistakes. */
@Service
public class PlateLoanService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PlateRepository plates;
    private final EmployeeRepository employees;
    private final PlateLoanRepository loans;
    private final PlateLoanAuditRepository audits;
    private final Clock clock;

    public PlateLoanService(PlateRepository plates, EmployeeRepository employees, PlateLoanRepository loans,
                            PlateLoanAuditRepository audits, Clock clock) {
        this.plates = plates;
        this.employees = employees;
        this.loans = loans;
        this.audits = audits;
        this.clock = clock;
    }

    /** "Take licence plate" button: records the current time as time out. */
    @Transactional
    public PlateLoan takePlate(Long plateId, Long employeeId, Long approverId, String stockNumber) {
        if (plateId == null || employeeId == null || approverId == null) {
            throw new BusinessRuleException("Plate, employee and approver are all required.");
        }
        String stock = normalizeStock(stockNumber);
        if (stock.isEmpty()) {
            throw new BusinessRuleException("Stock # is required.");
        }

        Plate plate = plates.findByIdForUpdate(plateId)
                .orElseThrow(() -> new BusinessRuleException("That plate does not exist."));
        if (!plate.isActive()) {
            throw new BusinessRuleException("Plate " + plate.getPlateNumber() + " is not in service.");
        }
        if (loans.existsByPlateIdAndTimeInIsNull(plateId)) {
            throw new BusinessRuleException("Plate " + plate.getPlateNumber() + " is already out.");
        }

        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new BusinessRuleException("That employee does not exist."));
        if (!employee.isActive()) {
            throw new BusinessRuleException(employee.getName() + " is not an active employee.");
        }

        Employee approver = employees.findById(approverId)
                .orElseThrow(() -> new BusinessRuleException("That approver does not exist."));
        if (!approver.isActive() || !Department.RECEPTION.equals(approver.getDepartment().getName())) {
            throw new BusinessRuleException("The approver must be an active Reception employee.");
        }

        PlateLoan loan = new PlateLoan();
        loan.setPlate(plate);
        loan.setEmployee(employee);
        loan.setApprover(approver);
        loan.setStockNumber(stock);
        loan.setTimeOut(LocalDateTime.now(clock));
        return loans.save(loan);
    }

    /**
     * "Return licence plate" button: records the current time as time in.
     *
     * @param actingEmployeeId the employee doing the return, or null when the caller may return any plate
     *                         (admin or shared kiosk login)
     */
    @Transactional
    public PlateLoan returnPlate(Long loanId, Long actingEmployeeId) {
        PlateLoan loan = loans.findWithDetailsById(loanId)
                .orElseThrow(() -> new BusinessRuleException("That log entry does not exist."));
        if (!loan.isOpen()) {
            throw new BusinessRuleException("Plate " + loan.getPlate().getPlateNumber() + " was already returned.");
        }
        if (actingEmployeeId != null && !actingEmployeeId.equals(loan.getEmployee().getId())) {
            throw new BusinessRuleException("Only " + loan.getEmployee().getName() + " can return this plate.");
        }
        loan.setTimeIn(LocalDateTime.now(clock));
        return loan;
    }

    /**
     * Admin correction of a log entry (forgotten return, typo in the stock #, wrong name...).
     * Every changed field is written to the audit trail with who, when and why. The plate itself cannot be changed.
     * Times are compared at minute precision because the edit form only has minutes, so untouched fields are not "changed".
     */
    @Transactional
    public PlateLoan correctLoan(Long loanId, LoanCorrection c, String changedBy) {
        PlateLoan loan = loans.findWithDetailsById(loanId)
                .orElseThrow(() -> new BusinessRuleException("That log entry does not exist."));

        String reason = c.reason() == null ? "" : c.reason().trim();
        if (reason.isEmpty()) {
            throw new BusinessRuleException("A reason for the correction is required.");
        }
        String stock = normalizeStock(c.stockNumber());
        if (stock.isEmpty()) {
            throw new BusinessRuleException("Stock # is required.");
        }
        if (c.timeOut() == null) {
            throw new BusinessRuleException("Time out is required.");
        }
        if (c.timeIn() != null && c.timeIn().isBefore(c.timeOut())) {
            throw new BusinessRuleException("Time in cannot be before time out.");
        }
        if (c.employeeId() == null || c.approverId() == null) {
            throw new BusinessRuleException("Employee and approver are required.");
        }

        // Past entries may name people who have since been deactivated, so "active" is not required here.
        Employee employee = employees.findById(c.employeeId())
                .orElseThrow(() -> new BusinessRuleException("That employee does not exist."));
        Employee approver = employees.findById(c.approverId())
                .orElseThrow(() -> new BusinessRuleException("That approver does not exist."));
        if (!Department.RECEPTION.equals(approver.getDepartment().getName())) {
            throw new BusinessRuleException("The approver must be a Reception employee.");
        }

        boolean reopening = loan.getTimeIn() != null && c.timeIn() == null;
        if (reopening) {
            Plate plate = loan.getPlate();
            plates.findByIdForUpdate(plate.getId());
            if (loans.existsByPlateIdAndTimeInIsNullAndIdNot(plate.getId(), loanId)) {
                throw new BusinessRuleException("Plate " + plate.getPlateNumber()
                        + " is out on another entry, so this one cannot be marked as still out.");
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<PlateLoanAudit> changes = new ArrayList<>();

        if (!loan.getEmployee().getId().equals(employee.getId())) {
            changes.add(audit(loan, changedBy, now, reason, "Employee", loan.getEmployee().getName(), employee.getName()));
            loan.setEmployee(employee);
        }
        if (!loan.getApprover().getId().equals(approver.getId())) {
            changes.add(audit(loan, changedBy, now, reason, "Approver", loan.getApprover().getName(), approver.getName()));
            loan.setApprover(approver);
        }
        if (!loan.getStockNumber().equals(stock)) {
            changes.add(audit(loan, changedBy, now, reason, "Stock #", loan.getStockNumber(), stock));
            loan.setStockNumber(stock);
        }
        if (!sameMinute(loan.getTimeOut(), c.timeOut())) {
            changes.add(audit(loan, changedBy, now, reason, "Time out", stamp(loan.getTimeOut()), stamp(c.timeOut())));
            loan.setTimeOut(c.timeOut());
        }
        if (!sameMinute(loan.getTimeIn(), c.timeIn())) {
            changes.add(audit(loan, changedBy, now, reason, "Time in", stamp(loan.getTimeIn()), stamp(c.timeIn())));
            loan.setTimeIn(c.timeIn());
        }

        if (changes.isEmpty()) {
            throw new BusinessRuleException("Nothing was changed.");
        }
        audits.saveAll(changes);
        return loan;
    }

    private static String normalizeStock(String stockNumber) {
        return stockNumber == null ? "" : stockNumber.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean sameMinute(LocalDateTime a, LocalDateTime b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.truncatedTo(ChronoUnit.MINUTES).equals(b.truncatedTo(ChronoUnit.MINUTES));
    }

    private static String stamp(LocalDateTime t) {
        return t == null ? "(still out)" : STAMP.format(t);
    }

    private static PlateLoanAudit audit(PlateLoan loan, String changedBy, LocalDateTime at, String reason,
                                        String field, String oldValue, String newValue) {
        PlateLoanAudit a = new PlateLoanAudit();
        a.setLoan(loan);
        a.setChangedBy(changedBy);
        a.setChangedAt(at);
        a.setFieldName(field);
        a.setOldValue(oldValue);
        a.setNewValue(newValue);
        a.setReason(reason);
        return a;
    }
}
