package com.platetracker.config;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.platetracker.domain.AppUser;
import com.platetracker.domain.Department;
import com.platetracker.domain.Employee;
import com.platetracker.domain.Plate;
import com.platetracker.domain.Role;
import com.platetracker.repository.AppUserRepository;
import com.platetracker.repository.DepartmentRepository;
import com.platetracker.repository.EmployeeRepository;
import com.platetracker.repository.PlateRepository;

/** First-run seeding: the four departments, a bootstrap admin and (optionally) demo data. */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final List<String> DEPARTMENTS = List.of("Operations", "Sales", "Service", Department.RECEPTION);

    private final DepartmentRepository departments;
    private final EmployeeRepository employees;
    private final PlateRepository plates;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String adminUsername;
    private final String adminPassword;
    private final boolean demoData;

    public DataInitializer(DepartmentRepository departments, EmployeeRepository employees,
                           PlateRepository plates, AppUserRepository users,
                           PasswordEncoder encoder, Clock clock,
                           @Value("${app.bootstrap.admin-username}") String adminUsername,
                           @Value("${app.bootstrap.admin-password}") String adminPassword,
                           @Value("${app.demo-data:false}") boolean demoData) {
        this.departments = departments;
        this.employees = employees;
        this.plates = plates;
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.demoData = demoData;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String name : DEPARTMENTS) {
            if (!departments.existsByName(name)) {
                Department d = new Department();
                d.setName(name);
                departments.save(d);
            }
        }
        if (users.count() == 0) {
            users.save(user(adminUsername, adminPassword, Role.ADMIN, null));
        }
        if (demoData && plates.count() == 0) {
            loadDemoData();
        }
    }

    private void loadDemoData() {
        Department operations = departments.findByName("Operations").orElseThrow();
        Department sales = departments.findByName("Sales").orElseThrow();
        Department service = departments.findByName("Service").orElseThrow();
        Department reception = departments.findByName(Department.RECEPTION).orElseThrow();

        Employee alex = employee("Cedric Driver", operations);
        employee("Roberto Driver", operations);
        employee("Shihab Sales", sales);
        employee("Hailey Service", service);
        employee("Tea Reception", reception);
        employee("Gally Reception", reception);

        for (int i = 1; i <= 4; i++) {
            Plate plate = new Plate();
            plate.setPlateNumber("DEMO-100" + i);
            plate.setRegistrationDate(LocalDate.now(clock).minusMonths(i));
            plates.save(plate);
        }

        // Demo logins: driver1 is tied to Cedric; kiosk is a shared login that picks the employee from a dropdown.
        users.save(user("driver1", "driver123", Role.EMPLOYEE, alex));
        users.save(user("kiosk", "kiosk123", Role.EMPLOYEE, null));
    }

    private Employee employee(String name, Department department) {
        Employee e = new Employee();
        e.setName(name);
        e.setDepartment(department);
        return employees.save(e);
    }

    private AppUser user(String username, String rawPassword, Role role, Employee employee) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setRole(role);
        u.setEmployee(employee);
        return u;
    }
}
