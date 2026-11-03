package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;

import org.junit.Test;

public class DimScheduleTest {
    private final DimSchedule night = new DimSchedule(21 * 60, 7 * 60, 70);

    private static LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute);
    }

    @Test
    public void dimsOvernightAcrossMidnight() {
        assertEquals(0, night.level(at(3, 20, 59), 0, null));
        assertEquals(70, night.level(at(3, 21, 0), 0, null));
        assertEquals(70, night.level(at(4, 3, 0), 0, null));
        assertEquals(0, night.level(at(4, 7, 0), 0, null));
    }

    @Test
    public void aPickHoldsUntilTheNextSwitch() {
        String madeIn = night.periodStart(at(3, 22, 30)).toString();   // turned off at 22:30
        assertEquals(0, night.level(at(4, 6, 59), 0, madeIn));
        assertEquals(0, night.level(at(4, 12, 0), 0, madeIn));
        assertEquals(70, night.level(at(4, 21, 0), 0, madeIn));        // next night dims again
    }

    @Test
    public void aDaytimePickEndsAtNight() {
        String madeIn = night.periodStart(at(3, 15, 0)).toString();    // 50% in the afternoon
        assertEquals(50, night.level(at(3, 20, 0), 50, madeIn));
        assertEquals(70, night.level(at(3, 21, 0), 50, madeIn));
    }

    @Test
    public void periodsAndSwitches() {
        assertEquals(at(2, 21, 0), night.periodStart(at(3, 3, 0)));
        assertEquals(at(3, 7, 0), night.periodStart(at(3, 7, 0)));
        assertEquals(at(3, 7, 0), night.nextSwitch(at(3, 3, 0)));
        assertEquals(at(4, 7, 0), night.nextSwitch(at(3, 21, 0)));
    }

    @Test
    public void sameDayWindow() {
        DimSchedule s = new DimSchedule(13 * 60, 15 * 60, 50);
        assertTrue(s.isNight(at(3, 14, 0)));
        assertFalse(s.isNight(at(3, 15, 0)));
    }

    @Test
    public void offWithoutALevelOrAWindow() {
        assertFalse(new DimSchedule(21 * 60, 7 * 60, 0).on());
        assertFalse(new DimSchedule(60, 60, 70).on());
        assertEquals(30, new DimSchedule(21 * 60, 7 * 60, 0).level(at(3, 23, 0), 30, null));
    }
}
