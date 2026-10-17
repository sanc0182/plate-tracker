package com.platetracker.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.platetracker.domain.PlateLoanAudit;

public interface PlateLoanAuditRepository extends JpaRepository<PlateLoanAudit, Long> {

    List<PlateLoanAudit> findByLoanIdOrderByChangedAtDescIdDesc(Long loanId);
}
