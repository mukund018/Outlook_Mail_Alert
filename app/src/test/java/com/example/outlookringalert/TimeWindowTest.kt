package com.example.outlookringalert

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Since TimePreferences uses Calendar.getInstance() internally, we'll create a testable version
 * of the logic to verify the windowcalculations.
 */
class TimeWindowTest {

    private fun isTimeInRange(
        currentHour: Int, currentMinute: Int,
        startHour: Int, startMinute: Int,
        endHour: Int, endMinute: Int
    ): Boolean {
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        val startTimeInMinutes = startHour * 60 + startMinute
        val endTimeInMinutes = endHour * 60 + endMinute

        return if (startTimeInMinutes < endTimeInMinutes) {
            currentTimeInMinutes in startTimeInMinutes until endTimeInMinutes
        } else {
            currentTimeInMinutes >= startTimeInMinutes || currentTimeInMinutes < endTimeInMinutes
        }
    }

    @Test
    fun testNormalWindow() {
        // 9 AM to 5 PM
        assertTrue(isTimeInRange(10, 0, 9, 0, 17, 0))
        assertTrue(isTimeInRange(9, 0, 9, 0, 17, 0))
        assertFalse(isTimeInRange(17, 0, 9, 0, 17, 0))
        assertFalse(isTimeInRange(8, 59, 9, 0, 17, 0))
    }

    @Test
    fun testMidnightCrossover() {
        // 10 PM to 6 AM
        assertTrue(isTimeInRange(23, 0, 22, 0, 6, 0))
        assertTrue(isTimeInRange(22, 0, 22, 0, 6, 0))
        assertTrue(isTimeInRange(5, 59, 22, 0, 6, 0))
        assertTrue(isTimeInRange(0, 0, 22, 0, 6, 0))
        assertFalse(isTimeInRange(6, 0, 22, 0, 6, 0))
        assertFalse(isTimeInRange(21, 59, 22, 0, 6, 0))
        assertFalse(isTimeInRange(12, 0, 22, 0, 6, 0))
    }

    @Test
    fun testDefaultUserWindow() {
        // 12 AM to 6 AM (User request)
        assertTrue(isTimeInRange(0, 0, 0, 0, 6, 0))
        assertTrue(isTimeInRange(3, 0, 0, 0, 6, 0))
        assertTrue(isTimeInRange(5, 59, 0, 0, 6, 0))
        assertFalse(isTimeInRange(6, 0, 0, 0, 6, 0))
        assertFalse(isTimeInRange(23, 59, 0, 0, 6, 0))
    }

    @Test
    fun testFullDayWindow() {
        // 12 AM to 12 AM (Start == End)
        assertTrue(isTimeInRange(0, 0, 0, 0, 0, 0))
        assertTrue(isTimeInRange(12, 0, 0, 0, 0, 0))
        assertTrue(isTimeInRange(23, 59, 0, 0, 0, 0))
    }
}
