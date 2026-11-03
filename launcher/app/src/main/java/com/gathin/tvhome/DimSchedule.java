package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Dim screen's night schedule (config.xml, dim_night_*): nightLevel from start until end, Off the rest of the day.
 * A level picked in the menu holds until the next switch time; the day is split into periods at the two switch
 * times, and a pick only counts in the period it was made in.
 */
final class DimSchedule {
    final int startMinute;
    final int endMinute;
    final int nightLevel;

    DimSchedule(int startMinute, int endMinute, int nightLevel) {
        this.startMinute = Math.floorMod(startMinute, 1440);
        this.endMinute = Math.floorMod(endMinute, 1440);
        this.nightLevel = nightLevel;
    }

    boolean on() {
        return nightLevel > 0 && startMinute != endMinute;
    }

    boolean isNight(LocalDateTime t) {
        int m = t.getHour() * 60 + t.getMinute();
        return startMinute < endMinute ? m >= startMinute && m < endMinute : m >= startMinute || m < endMinute;
    }

    /** The switch time this period began at: the latest start or end at or before {@code t}. */
    LocalDateTime periodStart(LocalDateTime t) {
        LocalDateTime best = null;
        for (int dayBack = 0; dayBack <= 1; dayBack++) {
            LocalDate d = t.toLocalDate().minusDays(dayBack);
            for (int minute : new int[]{startMinute, endMinute}) {
                LocalDateTime b = d.atTime(LocalTime.of(minute / 60, minute % 60));
                if (!b.isAfter(t) && (best == null || b.isAfter(best))) best = b;
            }
        }
        return best;
    }

    /** The first start or end after {@code t}. */
    LocalDateTime nextSwitch(LocalDateTime t) {
        LocalDateTime best = null;
        for (int dayAhead = 0; dayAhead <= 1; dayAhead++) {
            LocalDate d = t.toLocalDate().plusDays(dayAhead);
            for (int minute : new int[]{startMinute, endMinute}) {
                LocalDateTime b = d.atTime(LocalTime.of(minute / 60, minute % 60));
                if (b.isAfter(t) && (best == null || b.isBefore(best))) best = b;
            }
        }
        return best;
    }

    /** The level to show at {@code t}, given the last menu pick and the period key it was made in (null if none). */
    int level(LocalDateTime t, int picked, String pickedIn) {
        if (!on()) return picked;
        if (periodStart(t).toString().equals(pickedIn)) return picked;
        return isNight(t) ? nightLevel : 0;
    }
}
