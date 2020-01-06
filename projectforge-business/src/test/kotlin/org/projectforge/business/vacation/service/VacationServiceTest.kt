/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2020 Micromata GmbH, Germany (www.micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.business.vacation.service

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.projectforge.business.fibu.EmployeeDO
import org.projectforge.business.fibu.EmployeeDao
import org.projectforge.business.user.UserDao
import org.projectforge.business.vacation.model.RemainingDaysOfVactionDao
import org.projectforge.business.vacation.model.VacationDO
import org.projectforge.business.vacation.model.VacationStatus
import org.projectforge.business.vacation.repository.VacationDao
import org.projectforge.framework.access.AccessException
import org.projectforge.framework.i18n.UserException
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.framework.time.PFDayUtils
import org.projectforge.test.AbstractTestBase
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month

class VacationServiceTest : AbstractTestBase() {
    @Autowired
    private lateinit var employeeDao: EmployeeDao

    @Autowired
    private lateinit var userDao: UserDao

    @Autowired
    private lateinit var remainingDaysOfVactionDao: RemainingDaysOfVactionDao

    @Autowired
    private lateinit var vacationDao: VacationDao

    @Autowired
    private lateinit var vacationService: VacationService

    /**
     * Test is based on year 2020 (should also run in 2021...).
     */
    @Test
    fun employeeJoined2018WithoutVacationsYearTest() {
        val employee = createEmployee("2018-joiner-without-vacations", LocalDate.of(2018, Month.MAY, 1))
        logon(employee.user)
        // No vacation in 2019: joined in May, therefore 30/12*8=20 days should remain:
        assertStats(employee, 2019,
                vacationDaysInYearFromContract = 30.0) // Full year
        assertStats(employee, 2020,
                vacationDaysLeftInYear = 60.0,
                carryVacationDaysFromPreviousYear = 30.0) // Employee joined in 2018, carry expected.
        addVacations(employee, 2020, Month.JANUARY, 1, Month.JANUARY, 20, true)
        val stats = assertStats(employee, 2020,
                vacationDaysLeftInYear = 60.0,
                carryVacationDaysFromPreviousYear = 30.0) // Employee joined in 2018, carry expected.

        assertNumbers(stats, 13.0, stats.specialVacationDaysApproved, "specialVacationDaysInProgress")
    }

    /**
     * Test is based on year 2020 (should also run in 2021...).
     */
    @Test
    fun employeeJoined2018YearTest() {
        val employee = createEmployee("2018-joiner", LocalDate.of(2018, Month.MAY, 1))
        logon(employee.user)
        Assertions.assertEquals(20.0, addVacations(employee, 2019, Month.JULY, 1, Month.JULY, 26), "days off expected.")
        Assertions.assertEquals(10.0, addVacations(employee, 2020, Month.JULY, 13, Month.JULY, 24), "days off expected.")
        assertStats(employee, 2019,
                vacationDaysAllocatedInYear = 20.0,
                vacationDaysInYearFromContract = 30.0) // Full year
        assertStats(employee, 2020,
                vacationDaysLeftInYear = 30.0, // 30 from contract + 10 carry - 10 used
                vacationDaysAllocatedInYear = 10.0,
                carryVacationDaysFromPreviousYear = 10.0) // Employee joined in 2018, carry expected.
        assertStats(employee, 2020,
                baseMonth = Month.JUNE,
                vacationDaysLeftInYear = 20.0, // 30 from contract - 10 used
                vacationDaysAllocatedInYear = 10.0,
                carryVacationDaysFromPreviousYear = 10.0) // Employee joined in 2018, carry expected.

        try {
            addVacations(employee, 2020, Month.JULY, 20, Month.JULY, 28)
            fail("UserException expected due to collision of vacation entries.")
        } catch (ex: Exception) {
            Assertions.assertTrue(ex is UserException)
            Assertions.assertEquals(VacationValidator.Error.COLLISION.messageKey, ex.message)
        }
        logon(createEmployee("Foreign-user", LocalDate.of(2017, Month.JANUARY, 1)).user)
        try {
            addVacations(employee, 2020, Month.JULY, 20, Month.JULY, 28)
        } catch (ex: Exception) {
            Assertions.assertTrue(ex is AccessException)
        }
    }

    /**
     * Test is based on year 2020 (should also run in 2021...).
     */
    @Test
    fun employeeJoined2019WithoutVacationsYearTest() {
        val employee = createEmployee("2019-joiner-without-vacations", LocalDate.of(2019, Month.MAY, 1))
        logon(employee.user)
        // No vacation in 2019: joined in May, therefore 30/12*8=20 days should remain:
        assertStats(employee, 2019,
                vacationDaysInYearFromContract = 20.0) // 7 months
        assertStats(employee, 2020,
                carryVacationDaysFromPreviousYear = 20.0,
                vacationDaysLeftInYear = 50.0)
    }

    /**
     * Test is based on year 2020 (should also run in 2021...).
     */
    @Test
    fun employeeJoined2019YearTest() {
        val employee = createEmployee("2019-joiner", LocalDate.of(2019, Month.MAY, 1))
        logon(employee.user)
        // 10 days
        Assertions.assertEquals(10.0, addVacations(employee, 2019, Month.JULY, 1, Month.JULY, 12), "days off expected.")
        // 24.12.2018 (0.5), 27./30. (2), 31.12.2018 (0.5) -> 3 days, 2.-4.1. -> 3 days, total -> 6 days
        // Not yet allowed: Assertions.assertEquals(6.0, addVacations(employee, 2019, Month.DECEMBER, 24, Month.JANUARY, 6), "days off expected.")
        Assertions.assertEquals(3.0, addVacations(employee, 2019, Month.DECEMBER, 24, Month.DECEMBER, 31), "days off expected.")
        Assertions.assertEquals(3.0, addVacations(employee, 2020, Month.JANUARY, 1, Month.JANUARY, 6), "days off expected.")

        assertStats(employee, 2019,
                vacationDaysInYearFromContract = 20.0, // 7 months
                vacationDaysAllocatedInYear = 13.0)

        assertStats(employee, 2020,
                carryVacationDaysFromPreviousYear = 7.0,
                carryVacationDaysFromPreviousYearUnused = 4.0,
                vacationDaysInYearFromContract = 30.0, // Full year
                vacationDaysAllocatedInYear = 3.0,
                vacationDaysLeftInYear = 34.0) // 7 + 30 - 3 (used days)
        assertStats(employee, 2020,
                baseMonth = Month.JUNE,
                carryVacationDaysFromPreviousYear = 7.0,
                carryVacationDaysFromPreviousYearUnused = 4.0,
                vacationDaysAllocatedInYear = 3.0,
                vacationDaysLeftInYear = 30.0) // 4 days lost after overlap period: 7 - 4 + 30 - 3

        Assertions.assertEquals(25.0, addVacations(employee, 2020, Month.JUNE, 1, Month.JULY, 7), "days off expected.")
        assertStats(employee, 2020,
                carryVacationDaysFromPreviousYear = 7.0,
                carryVacationDaysFromPreviousYearUnused = 4.0,
                vacationDaysAllocatedInYear = 28.0,
                vacationDaysLeftInYear = 9.0) // 7 + 30 - 28 (used days)
        assertStats(employee, 2020,
                baseMonth = Month.JUNE,
                carryVacationDaysFromPreviousYear = 7.0,
                carryVacationDaysFromPreviousYearUnused = 4.0,
                vacationDaysAllocatedInYear = 28.0,
                vacationDaysLeftInYear = 5.0) // 4 days lost after overlap period: 7 - 4 + 30 - 28

        try {
            Assertions.assertEquals(7.0, addVacations(employee, 2020, Month.APRIL, 1, Month.APRIL, 10), "days off expected.")
            fail("UserException expected due to collision of vacation entries.")
        } catch (ex: Exception) {
            Assertions.assertTrue(ex is UserException)
            Assertions.assertEquals(VacationValidator.Error.NOT_ENOUGH_DAYS_LEFT.messageKey, ex.message)
        }
    }

    /**
     * Test is based on year 2020 (should also run in 2021...).
     */
    @Test
    fun employeeJoinedThisYearTest() {
        val employee = createEmployee("2020-joiner", LocalDate.of(2020, Month.MAY, 1))
        logon(employee.user)
        assertStats(employee, 2020,
                vacationDaysInYearFromContract = 20.0)
        // 1.6. is an holiday (Pfingstmontag):
        Assertions.assertEquals(7.0, addVacations(employee, 2020, Month.JUNE, 1, Month.JUNE, 10), "days off expected.")
        assertStats(employee, 2020,
                vacationDaysInYearFromContract = 20.0,
                vacationDaysAllocatedInYear = 7.0)
    }

    @Test
    fun employeeJoinedInFutureTest() {
        val employee = createEmployee("FutureJoiner", LocalDate.now().plusDays(1))
        logon(employee.user)
        assertStats(employee, 2020)
    }

    /**
     * If endMonth is before startMonth, the next year will be used as endYear.
     * @return Number of vacation days (equals to working days between startDate and endDate)
     */
    private fun addVacations(employee: EmployeeDO, startYear: Int, startMonth: Month, startDay: Int, endMonth: Month, endDay: Int, special: Boolean = false): Double {
        val endYear = if (startMonth > endMonth)
            startYear + 1 // Vacations over years.
        else
            startYear
        return addVacations(employee, LocalDate.of(startYear, startMonth, startDay), LocalDate.of(endYear, endMonth, endDay), special)
    }

    /**
     * Ensures vacation days only after join date of this employee.
     * @return Number of vacation days (equals to working days between startDate and endDate)
     */
    private fun addVacations(employee: EmployeeDO, startDate: LocalDate, endDate: LocalDate, special: Boolean = false): Double {
        if (endDate.isBefore(employee.eintrittsDatum))
            return 0.0
        val vacation = VacationDO()
        vacation.employee = employee
        vacation.startDate = if (startDate.isBefore(employee.eintrittsDatum)) employee.eintrittsDatum else startDate
        vacation.endDate = endDate
        vacation.halfDay = false
        vacation.special = false
        vacation.status = VacationStatus.APPROVED
        vacation.manager = employee // OK for tests...
        vacation.special = special
        vacationDao.save(vacation)
        return PFDayUtils.getNumberOfWorkingDays(startDate, endDate).toDouble()
    }

    private fun createEmployee(name: String, joinDate: LocalDate?, leaveDate: LocalDate? = null): EmployeeDO {
        val user = PFUserDO()
        user.firstname = name
        user.lastname = name
        user.username = "$name.$name"
        userDao.internalSave(user)
        val employee = EmployeeDO()
        employee.user = user
        employee.eintrittsDatum = joinDate
        employee.austrittsDatum = leaveDate
        employee.urlaubstage = 30
        employeeDao.internalSave(employee)
        return employee
    }

    private fun assertStats(employee: EmployeeDO,
                            year: Int,
                            carryVacationDaysFromPreviousYear: Double = 0.0,
                            carryVacationDaysFromPreviousYearUnused: Double = carryVacationDaysFromPreviousYear,
                            vacationDaysInYearFromContract: Double = 30.0,
                            vacationDaysAllocatedInYear: Double = 0.0,
                            vacationDaysLeftInYear: Double? = null,
                            /**
                             * Inside overlap time (before end of vacation year 31.03.2020, default) or after.
                             */
                            baseMonth: Month = Month.JANUARY): VacationStats {
        val stats = vacationService.getVacationStats(employee, year, true, LocalDate.of(2020, baseMonth, 15))
        assertNumbers(stats, carryVacationDaysFromPreviousYear, stats.carryVacationDaysFromPreviousYear, "carryVacationDaysFromPreviousYear")
        assertNumbers(stats, carryVacationDaysFromPreviousYearUnused, stats.carryVacationDaysFromPreviousYearUnused, "carryVacationDaysFromPreviousYearUnused")
        assertNumbers(stats, vacationDaysAllocatedInYear, stats.vacationDaysInProgressAndApproved, "vacationDaysAllocatedInYear")
        assertNumbers(stats, vacationDaysInYearFromContract, stats.vacationDaysInYearFromContract, "vacationDaysInYearFromContract")
        if (vacationDaysLeftInYear != null)
            assertNumbers(stats, vacationDaysLeftInYear, stats.vacationDaysLeftInYear, "vacationDaysLeftInYear")
        else
            assertNumbers(stats, vacationDaysInYearFromContract - vacationDaysAllocatedInYear, stats.vacationDaysLeftInYear, "vacationDaysLeftInYear")
        if (carryVacationDaysFromPreviousYear != null && carryVacationDaysFromPreviousYear > 0) {
            assertNumbers(stats,
                    carryVacationDaysFromPreviousYear,
                    remainingDaysOfVactionDao.internalGet(stats.employee.id, stats.year)?.carryVacationDaysFromPreviousYear,
                    "carryVacationDaysFromPreviousYear in db: $stats")
        }
        return stats
    }

    private fun assertNumbers(stats: VacationStats, expected: Double?, actual: BigDecimal?, msg: String) {
        if (expected == null) {
            Assertions.assertNull(actual, "$msg: $stats")
        } else {
            Assertions.assertEquals(expected, actual?.toDouble(), "$msg: $stats")
        }
    }


    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            VacationValidator.rejectNewVacationEntriesBeforeNow = false
        }
    }
}
