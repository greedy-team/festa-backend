package com.greedy.festa.importer.model;

import java.util.Map;

public record ParsedCsvRow(int line, Map<String, String> values) {
}
