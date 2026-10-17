package com.platetracker.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.platetracker.domain.Employee;
import com.platetracker.domain.PlateLoan;
import com.platetracker.repository.EmployeeRepository;
import com.platetracker.repository.PlateLoanAuditRepository;
import com.platetracker.service.PlateLoanService;
import com.platetracker.repository.PlateLoanRepository;
import com.platetracker.repository.PlateRepository;

/** Makes sure every page renders (templates + lazy loading) and the role rules hold. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WebSmokeTest {

    @Autowired MockMvc mvc;
    @Autowired PlateRepository plates;
    @Autowired EmployeeRepository employees;
    @Autowired PlateLoanRepository loans;
    @Autowired PlateLoanAuditRepository audits;
    @Autowired PlateLoanService service;

    @Test
    void anonymousUsersAreSentToLogin() throws Exception {
        mvc.perform(get("/plates")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "driver1", roles = "EMPLOYEE")
    void employeeSeesTheTakeAndReturnPage() throws Exception {
        mvc.perform(get("/plates")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Take a licence plate")));
    }

    @Test
    @WithMockUser(username = "driver1", roles = "EMPLOYEE")
    void employeeCannotOpenAdminPages() throws Exception {
        for (String path : new String[] { "/admin/loans", "/admin/plates", "/admin/employees",
                "/admin/departments", "/admin/users" }) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPagesRender() throws Exception {
        for (String path : new String[] { "/plates", "/admin/loans", "/admin/plates", "/admin/employees",
                "/admin/departments", "/admin/users" }) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        // edit mode
        mvc.perform(get("/admin/employees").param("edit", String.valueOf(employees.findAll().get(0).getId())))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/plates").param("edit", String.valueOf(plates.findAll().get(0).getId())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "driver1", roles = "EMPLOYEE")
    void employeeCanTakeThenReturnAPlateThroughTheUi() throws Exception {
        Employee receptionist = employees.findByActiveTrueAndDepartmentNameOrderByNameAsc("Reception").get(0);
        Long plateId = plates.findAll().get(0).getId();

        mvc.perform(post("/plates/take").with(csrf())
                        .param("plateId", plateId.toString())
                        .param("approverId", receptionist.getId().toString())
                        .param("stockNumber", "ab99"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/plates"));

        var open = loans.findByTimeInIsNullOrderByTimeOutDesc();
        assertThat(open).hasSize(1);
        assertThat(open.get(0).getEmployee().getName()).isEqualTo("Alex Driver"); // forced to the linked employee
        assertThat(open.get(0).getStockNumber()).isEqualTo("AB99");

        mvc.perform(get("/plates")).andExpect(status().isOk());

        mvc.perform(post("/plates/{id}/return", open.get(0).getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(loans.findByTimeInIsNullOrderByTimeOutDesc()).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void duplicatePlateNumbersAreRejectedWithAMessage() throws Exception {
        String existing = plates.findAll().get(0).getPlateNumber();
        mvc.perform(post("/admin/plates").with(csrf())
                        .param("plateNumber", existing)
                        .param("registrationDate", "2026-01-15")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("already exists")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanCorrectALogEntryAndSeeItsHistory() throws Exception {
        Employee driver = employees.findByActiveTrueAndDepartmentNameOrderByNameAsc("Operations").get(0);
        Employee receptionist = employees.findByActiveTrueAndDepartmentNameOrderByNameAsc("Reception").get(0);
        PlateLoan loan = service.takePlate(plates.findAll().get(0).getId(), driver.getId(), receptionist.getId(), "S1");
        String out = loan.getTimeOut().truncatedTo(ChronoUnit.MINUTES)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));

        mvc.perform(get("/admin/loans/{id}/edit", loan.getId())).andExpect(status().isOk());

        // missing reason: the page is shown again with an error, nothing is saved
        mvc.perform(post("/admin/loans/{id}/edit", loan.getId()).with(csrf())
                        .param("employeeId", driver.getId().toString())
                        .param("approverId", receptionist.getId().toString())
                        .param("stockNumber", "S2").param("timeOut", out).param("timeIn", "")
                        .param("reason", ""))
                .andExpect(status().isOk());
        assertThat(loans.findById(loan.getId()).orElseThrow().getStockNumber()).isEqualTo("S1");

        // valid correction: redirected back to the log, change and audit row saved
        mvc.perform(post("/admin/loans/{id}/edit", loan.getId()).with(csrf())
                        .param("employeeId", driver.getId().toString())
                        .param("approverId", receptionist.getId().toString())
                        .param("stockNumber", "S2").param("timeOut", out).param("timeIn", "")
                        .param("reason", "Typo in stock number"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/loans"));
        assertThat(loans.findById(loan.getId()).orElseThrow().getStockNumber()).isEqualTo("S2");
        assertThat(audits.findByLoanIdOrderByChangedAtDescIdDesc(loan.getId())).hasSize(1);

        mvc.perform(get("/admin/loans/{id}/edit", loan.getId())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Typo in stock number")));
    }

    @Test
    @WithMockUser(username = "driver1", roles = "EMPLOYEE")
    void employeeCannotCorrectLogEntries() throws Exception {
        mvc.perform(get("/admin/loans/1/edit")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/loans/1/edit").with(csrf())).andExpect(status().isForbidden());
    }
}
