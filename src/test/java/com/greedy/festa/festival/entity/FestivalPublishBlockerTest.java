package com.greedy.festa.festival.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
public class FestivalPublishBlockerTest {

    @Test
    public void 라인업_0건이면_LINEUP_EMPTY가_담긴다() {
        List<FestivalPublishBlocker> blockers = FestivalPublishBlocker.evaluate(
                true, 37.5509, 127.0743, 0
        );

        assertThat(blockers).containsExactly(FestivalPublishBlocker.LINEUP_EMPTY);
    }
}
