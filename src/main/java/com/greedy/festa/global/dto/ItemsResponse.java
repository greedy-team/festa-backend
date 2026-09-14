package com.greedy.festa.global.dto;

import java.util.List;

public record ItemsResponse<T>(List<T> items) {

    public static <T> ItemsResponse<T> of(List<T> items) {
        return new ItemsResponse<>(items);
    }
}
