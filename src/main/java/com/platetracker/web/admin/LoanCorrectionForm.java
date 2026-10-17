package com.platetracker.web.admin;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.format.annotation.DateTimeFormat;

import com.platetracker.domain.PlateLoan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Form for an admin correction. The plate is deliberately not editable. */
public class LoanCorrectionForm {

    private static final String PATTERN = "yyyy-MM-dd'T'HH:mm";

    @NotNull
    private Long employeeId;

    @NotNull
    private Long approverId;

    @NotBlank
    @Size(max = 30)
    private String stockNumber;

    @NotNull
    @DateTimeFormat(pattern = PATTERN)
    private LocalDateTime timeOut;

    /** Empty means the plate is still out. */
    @DateTimeFormat(pattern = PATTERN)
    private LocalDateTime timeIn;

    @NotBlank
    @Size(max = 255)
    private String reason;

    public static LoanCorrectionForm fromLoan(PlateLoan loan) {
        LoanCorrectionForm f = new LoanCorrectionForm();
        f.employeeId = loan.getEmployee().getId();
        f.approverId = loan.getApprover().getId();
        f.stockNumber = loan.getStockNumber();
        f.timeOut = loan.getTimeOut().truncatedTo(ChronoUnit.MINUTES);
        f.timeIn = loan.getTimeIn() == null ? null : loan.getTimeIn().truncatedTo(ChronoUnit.MINUTES);
        return f;
    }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }

    public String getStockNumber() { return stockNumber; }
    public void setStockNumber(String stockNumber) { this.stockNumber = stockNumber; }

    public LocalDateTime getTimeOut() { return timeOut; }
    public void setTimeOut(LocalDateTime timeOut) { this.timeOut = timeOut; }

    public LocalDateTime getTimeIn() { return timeIn; }
    public void setTimeIn(LocalDateTime timeIn) { this.timeIn = timeIn; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
