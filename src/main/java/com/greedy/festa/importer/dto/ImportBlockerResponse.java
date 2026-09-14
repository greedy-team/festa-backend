package com.greedy.festa.importer.dto;

import java.util.List;

public record ImportBlockerResponse(String code, long count, List<String> values) {
}
