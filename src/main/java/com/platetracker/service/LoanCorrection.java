package com.platetracker.service;

import java.time.LocalDateTime;

/** The corrected values of a log entry, plus the mandatory reason. A null {@code timeIn} means "still out". */
public record LoanCorrection(Long employeeId, Long approverId, String stockNumber,
                             LocalDateTime timeOut, LocalDateTime timeIn, String reason) {
}
