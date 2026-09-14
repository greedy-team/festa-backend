package com.greedy.festa.support.fixture;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.lineup.entity.Lineup;

public final class LineupFixture {

    public static final int DAY = 1;
    public static final int DISPLAY_ORDER = 1;

    private LineupFixture() {
    }

    public static Lineup.LineupBuilder lineup(Festival festival, Artist artist) {
        return Lineup.builder()
                .festival(festival)
                .artist(artist)
                .day(DAY)
                .displayOrder(DISPLAY_ORDER);
    }
}
