package com.platetracker.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One row of the paper sheet: a plate leaving the drawer and (later) coming back.
 * The sheet's "Date" column is simply the date part of {@link #timeOut}.
 */
@Entity
@Table(name = "plate_loan", indexes = @Index(name = "idx_loan_plate_return", columnList = "plate_id, time_in"))
public class PlateLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plate_id", nullable = false)
    private Plate plate;

    /** Who is driving. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** The receptionist who handed the plate out. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approver_id", nullable = false)
    private Employee approver;

    /** Stock # of the car the plate is used on. A plain string for now; see README for a Vehicle entity option. */
    @Column(name = "stock_number", nullable = false, length = 30)
    private String stockNumber;

    @Column(name = "time_out", nullable = false)
    private LocalDateTime timeOut;

    /** Null while the plate is still out. */
    @Column(name = "time_in")
    private LocalDateTime timeIn;

    public boolean isOpen() { return timeIn == null; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Plate getPlate() { return plate; }
    public void setPlate(Plate plate) { this.plate = plate; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public Employee getApprover() { return approver; }
    public void setApprover(Employee approver) { this.approver = approver; }

    public String getStockNumber() { return stockNumber; }
    public void setStockNumber(String stockNumber) { this.stockNumber = stockNumber; }

    public LocalDateTime getTimeOut() { return timeOut; }
    public void setTimeOut(LocalDateTime timeOut) { this.timeOut = timeOut; }

    public LocalDateTime getTimeIn() { return timeIn; }
    public void setTimeIn(LocalDateTime timeIn) { this.timeIn = timeIn; }
}
