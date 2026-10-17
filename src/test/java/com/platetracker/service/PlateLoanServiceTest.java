package com.platetracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.platetracker.domain.Employee;
import com.platetracker.domain.Plate;
import com.platetracker.domain.PlateLoan;
import com.platetracker.domain.PlateLoanAudit;
import com.platetracker.repository.EmployeeRepository;
import com.platetracker.repository.PlateLoanAuditRepository;
import com.platetracker.repository.PlateLoanRepository;
import com.platetracker.repository.PlateRepository;

@SpringBootTest
@Transactional
class PlateLoanServiceTest {

    @Autowired PlateLoanService service;
    @Autowired PlateRepository plates;
    @Autowired EmployeeRepository employees;
    @Autowired PlateLoanRepository loans;
    @Autowired PlateLoanAuditRepository audits;

    private Plate plate() {
        return plates.findAll().get(0);
    }

    private Employee employeeIn(String department) {
        return employees.findByActiveTrueAndDepartmentNameOrderByNameAsc(department).get(0);
    }

    private PlateLoan takeOne(String stock) {
        return service.takePlate(plate().getId(), employeeIn("Operations").getId(), employeeIn("Reception").getId(), stock);
    }

    /** A correction that changes nothing, as a starting point for the tests below. */
    private LoanCorrection unchanged(PlateLoan loan, String reason) {
        return new LoanCorrection(loan.getEmployee().getId(), loan.getApprover().getId(), loan.getStockNumber(),
                loan.getTimeOut().truncatedTo(ChronoUnit.MINUTES),
                loan.getTimeIn() == null ? null : loan.getTimeIn().truncatedTo(ChronoUnit.MINUTES), reason);
    }

    // ---- taking and returning -------------------------------------------------------------------------------

    @Test
    void takingAndReturningRecordsTimes() {
        Employee driver = employeeIn("Operations");
        PlateLoan loan = service.takePlate(plate().getId(), driver.getId(), employeeIn("Reception").getId(), " st1234 ");

        assertThat(loan.getStockNumber()).isEqualTo("ST1234");
        assertThat(loan.getTimeOut()).isNotNull();
        assertThat(loan.isOpen()).isTrue();

        service.returnPlate(loan.getId(), driver.getId());

        PlateLoan reloaded = loans.findById(loan.getId()).orElseThrow();
        assertThat(reloaded.getTimeIn()).isNotNull().isAfterOrEqualTo(reloaded.getTimeOut());
        assertThat(reloaded.isOpen()).isFalse();
    }

    @Test
    void takenPlateIsNoLongerAvailable() {
        Plate plate = plate();
        int before = plates.findAvailable().size();

        service.takePlate(plate.getId(), employeeIn("Sales").getId(), employeeIn("Reception").getId(), "S1");

        assertThat(plates.findAvailable()).hasSize(before - 1)
                .extracting(Plate::getId).doesNotContain(plate.getId());
    }

    @Test
    void plateCannotBeTakenTwice() {
        Plate plate = plate();
        Employee driver = employeeIn("Operations");
        Employee receptionist = employeeIn("Reception");
        service.takePlate(plate.getId(), driver.getId(), receptionist.getId(), "S1");

        assertThatThrownBy(() -> service.takePlate(plate.getId(), driver.getId(), receptionist.getId(), "S2"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already out");
    }

    @Test
    void anEmployeeMayHaveSeveralPlatesOutAtOnce() {
        Employee driver = employeeIn("Operations");
        Employee receptionist = employeeIn("Reception");
        List<Plate> all = plates.findAll();

        service.takePlate(all.get(0).getId(), driver.getId(), receptionist.getId(), "S1");
        service.takePlate(all.get(1).getId(), driver.getId(), receptionist.getId(), "S2");

        assertThat(loans.findByTimeInIsNullOrderByTimeOutDesc()).hasSize(2);
    }

    @Test
    void approverMustBeFromReception() {
        Employee driver = employeeIn("Operations");

        assertThatThrownBy(() -> service.takePlate(plate().getId(), driver.getId(), driver.getId(), "S1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Reception");
    }

    @Test
    void stockNumberIsRequired() {
        assertThatThrownBy(() -> service.takePlate(plate().getId(), employeeIn("Operations").getId(),
                employeeIn("Reception").getId(), "   "))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Stock");
    }

    @Test
    void onlyTheBorrowerCanReturnThePlate() {
        Employee driver = employeeIn("Operations");
        Employee someoneElse = employeeIn("Sales");
        PlateLoan loan = service.takePlate(plate().getId(), driver.getId(), employeeIn("Reception").getId(), "S1");

        assertThatThrownBy(() -> service.returnPlate(loan.getId(), someoneElse.getId()))
                .isInstanceOf(BusinessRuleException.class);

        // A null acting employee (admin / shared kiosk login) may return anything.
        assertThat(service.returnPlate(loan.getId(), null).getTimeIn()).isNotNull();
    }

    @Test
    void aPlateCannotBeReturnedTwice() {
        Employee driver = employeeIn("Operations");
        PlateLoan loan = service.takePlate(plate().getId(), driver.getId(), employeeIn("Reception").getId(), "S1");
        service.returnPlate(loan.getId(), driver.getId());

        assertThatThrownBy(() -> service.returnPlate(loan.getId(), driver.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already returned");
    }

    // ---- admin corrections + audit trail --------------------------------------------------------------------

    @Test
    void correctingAnEntryRecordsOneAuditRowPerChangedField() {
        PlateLoan loan = takeOne("S1");
        LocalDateTime out = loan.getTimeOut().truncatedTo(ChronoUnit.MINUTES);

        service.correctLoan(loan.getId(),
                new LoanCorrection(loan.getEmployee().getId(), loan.getApprover().getId(), "s9", out, out.plusHours(2),
                        "Forgot to click return"),
                "admin");

        PlateLoan fixed = loans.findById(loan.getId()).orElseThrow();
        assertThat(fixed.getStockNumber()).isEqualTo("S9");
        assertThat(fixed.getTimeIn()).isEqualTo(out.plusHours(2));

        List<PlateLoanAudit> history = audits.findByLoanIdOrderByChangedAtDescIdDesc(loan.getId());
        // time out was only "re-submitted" at minute precision, so it must not show up as a change
        assertThat(history).extracting(PlateLoanAudit::getFieldName).containsExactlyInAnyOrder("Stock #", "Time in");
        assertThat(history).allSatisfy(a -> {
            assertThat(a.getChangedBy()).isEqualTo("admin");
            assertThat(a.getReason()).isEqualTo("Forgot to click return");
            assertThat(a.getChangedAt()).isNotNull();
        });
        PlateLoanAudit stock = history.stream().filter(a -> a.getFieldName().equals("Stock #")).findFirst().orElseThrow();
        assertThat(stock.getOldValue()).isEqualTo("S1");
        assertThat(stock.getNewValue()).isEqualTo("S9");
    }

    @Test
    void aCorrectionNeedsAReason() {
        PlateLoan loan = takeOne("S1");
        LoanCorrection c = unchanged(loan, "   ");

        assertThatThrownBy(() -> service.correctLoan(loan.getId(), c, "admin"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void aCorrectionThatChangesNothingIsRejected() {
        PlateLoan loan = takeOne("S1");

        assertThatThrownBy(() -> service.correctLoan(loan.getId(), unchanged(loan, "just checking"), "admin"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Nothing was changed");
        assertThat(audits.findByLoanIdOrderByChangedAtDescIdDesc(loan.getId())).isEmpty();
    }

    @Test
    void timeInCannotBeBeforeTimeOut() {
        PlateLoan loan = takeOne("S1");
        LocalDateTime out = loan.getTimeOut().truncatedTo(ChronoUnit.MINUTES);

        assertThatThrownBy(() -> service.correctLoan(loan.getId(),
                new LoanCorrection(loan.getEmployee().getId(), loan.getApprover().getId(), "S1", out, out.minusMinutes(5),
                        "typo"), "admin"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("before");
    }

    @Test
    void correctionCanChangeTheEmployee() {
        PlateLoan loan = takeOne("S1");
        Employee other = employeeIn("Sales");
        LoanCorrection c = new LoanCorrection(other.getId(), loan.getApprover().getId(), loan.getStockNumber(),
                loan.getTimeOut().truncatedTo(ChronoUnit.MINUTES), null, "Wrong name selected");

        service.correctLoan(loan.getId(), c, "admin");

        assertThat(loans.findById(loan.getId()).orElseThrow().getEmployee().getId()).isEqualTo(other.getId());
        assertThat(audits.findByLoanIdOrderByChangedAtDescIdDesc(loan.getId()))
                .singleElement().satisfies(a -> {
                    assertThat(a.getFieldName()).isEqualTo("Employee");
                    assertThat(a.getNewValue()).isEqualTo(other.getName());
                });
    }

    @Test
    void aReturnedEntryCanBeReopenedWhenThePlateIsNotOutElsewhere() {
        PlateLoan loan = takeOne("S1");
        service.returnPlate(loan.getId(), null);

        LoanCorrection reopen = new LoanCorrection(loan.getEmployee().getId(), loan.getApprover().getId(), "S1",
                loan.getTimeOut().truncatedTo(ChronoUnit.MINUTES), null, "Returned by mistake");
        service.correctLoan(loan.getId(), reopen, "admin");

        assertThat(loans.findById(loan.getId()).orElseThrow().isOpen()).isTrue();
        assertThat(audits.findByLoanIdOrderByChangedAtDescIdDesc(loan.getId()))
                .singleElement().satisfies(a -> {
                    assertThat(a.getFieldName()).isEqualTo("Time in");
                    assertThat(a.getNewValue()).isEqualTo("(still out)");
                });
    }

    @Test
    void aReturnedEntryCannotBeReopenedWhileThePlateIsOutOnAnotherEntry() {
        PlateLoan first = takeOne("S1");
        service.returnPlate(first.getId(), null);
        takeOne("S2"); // same plate goes out again

        LoanCorrection reopen = new LoanCorrection(first.getEmployee().getId(), first.getApprover().getId(), "S1",
                first.getTimeOut().truncatedTo(ChronoUnit.MINUTES), null, "oops");

        assertThatThrownBy(() -> service.correctLoan(first.getId(), reopen, "admin"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("another entry");
    }
}
