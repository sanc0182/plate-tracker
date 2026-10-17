package com.platetracker.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.platetracker.domain.Plate;

import jakarta.persistence.LockModeType;

public interface PlateRepository extends JpaRepository<Plate, Long> {

    boolean existsByPlateNumber(String plateNumber);

    boolean existsByPlateNumberAndIdNot(String plateNumber, Long id);

    /** Active plates that are not currently out. These fill the "take a plate" dropdown. */
    @Query("""
            select p from Plate p
            where p.active = true
              and not exists (select 1 from PlateLoan l where l.plate = p and l.timeIn is null)
            order by p.plateNumber
            """)
    List<Plate> findAvailable();

    /** Row lock so two people clicking "take" on the same plate at once can't both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Plate p where p.id = :id")
    Optional<Plate> findByIdForUpdate(@Param("id") Long id);
}
