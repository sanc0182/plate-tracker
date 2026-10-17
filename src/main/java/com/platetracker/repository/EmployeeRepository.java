package com.platetracker.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.platetracker.domain.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @EntityGraph(attributePaths = "department")
    @Override
    List<Employee> findAll(Sort sort);

    @EntityGraph(attributePaths = "department")
    @Override
    Optional<Employee> findById(Long id);

    @EntityGraph(attributePaths = "department")
    List<Employee> findByActiveTrueOrderByNameAsc();

    @EntityGraph(attributePaths = "department")
    List<Employee> findByActiveTrueAndDepartmentNameOrderByNameAsc(String departmentName);

    /** Includes inactive employees: needed when correcting historical log entries. */
    @EntityGraph(attributePaths = "department")
    List<Employee> findByDepartmentNameOrderByNameAsc(String departmentName);
}
