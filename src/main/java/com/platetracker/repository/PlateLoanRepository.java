package com.platetracker.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.platetracker.domain.PlateLoan;

public interface PlateLoanRepository extends JpaRepository<PlateLoan, Long> {

    @EntityGraph(attributePaths = { "plate", "employee", "approver" })
    @Override
    Page<PlateLoan> findAll(Pageable pageable);

    /** Plates currently out. */
    @EntityGraph(attributePaths = { "plate", "employee", "approver" })
    List<PlateLoan> findByTimeInIsNullOrderByTimeOutDesc();

    @EntityGraph(attributePaths = { "plate", "employee", "approver" })
    Optional<PlateLoan> findWithDetailsById(Long id);

    boolean existsByPlateIdAndTimeInIsNull(Long plateId);

    /** Is the plate out on some entry other than this one? Used when an admin reopens a returned entry. */
    boolean existsByPlateIdAndTimeInIsNullAndIdNot(Long plateId, Long id);
}
