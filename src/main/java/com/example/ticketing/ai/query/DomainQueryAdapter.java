package com.example.ticketing.ai.query;

import com.example.ticketing.authorization.ActorContext;

public interface DomainQueryAdapter {

    DomainDef definition();

    AccessRule accessRule();

    /** Applies row-level scope and filters in SQL. The role gate is QueryExecutor's job. */
    QueryResult execute(ValidatedPlan plan, ActorContext actor);
}
