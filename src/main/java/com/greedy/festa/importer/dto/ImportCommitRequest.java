package com.greedy.festa.importer.dto;

import java.util.List;
import java.util.Map;

public record ImportCommitRequest(Map<String, List<Integer>> lines) {
}
