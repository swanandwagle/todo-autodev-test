package com.example.todo.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.BasicType;
import org.hibernate.type.StandardBasicTypes;

/**
 * Registers custom Postgres functions used in JPA Specifications for array containment
 * and full-text search. These map directly to Postgres operators:
 *   array_contains_all(col, literal)  => col @> literal::text[]
 *   array_overlaps_any(col, literal)  => col && literal::text[]
 *   ts_match(vector, query)           => vector @@ plainto_tsquery('simple', query)
 *
 * Hibernate 7 (bundled with Boot 4.1) supports FunctionContributor via ServiceLoader.
 * This class is registered in META-INF/services/org.hibernate.boot.model.FunctionContributor.
 */
public class PostgresFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        var typeConfiguration = functionContributions.getTypeConfiguration();
        BasicType<Boolean> boolType = typeConfiguration.getBasicTypeRegistry()
                .resolve(StandardBasicTypes.BOOLEAN);

        // col @> literal::text[]
        functionContributions.getFunctionRegistry().registerPattern(
                "array_contains_all",
                "(?1 @> ?2::text[])",
                boolType
        );

        // col && literal::text[]
        functionContributions.getFunctionRegistry().registerPattern(
                "array_overlaps_any",
                "(?1 && ?2::text[])",
                boolType
        );

        // col @@ plainto_tsquery('simple', query)
        functionContributions.getFunctionRegistry().registerPattern(
                "ts_match",
                "(?1 @@ plainto_tsquery('simple', ?2))",
                boolType
        );
    }
}
