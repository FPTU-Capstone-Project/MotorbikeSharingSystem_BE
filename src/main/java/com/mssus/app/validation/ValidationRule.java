package com.mssus.app.validation;

import com.mssus.app.common.exception.ValidationException;

/**
 * Represents a single, reusable business validation rule.
 *
 * @param <T> The type of the context object this rule validates.
 */
@FunctionalInterface
public interface ValidationRule<T> {
    void validate(T context) throws ValidationException;
}