package com.greedy.festa.importer.repository;

import com.greedy.festa.importer.entity.ImportCommitAction;
import com.greedy.festa.importer.entity.ImportCommitSection;

public interface ImportCommitAggregateRow {
    Long getBatchId();

    ImportCommitSection getSection();

    ImportCommitAction getAction();

    long getTotal();
}
