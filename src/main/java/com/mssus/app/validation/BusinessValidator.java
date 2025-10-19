package com.mssus.app.validation;

import com.mssus.app.common.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A chainable business rule validation engine.
 * It executes a series of validation rules and throws an exception on the first failure.
 */
@Component
public class BusinessValidator {

    public <T> ValidationChain<T> forContext(T context) {
        return new ValidationChain<>(context);
    }

    public static class ValidationChain<T> {
        private final T context;
        private final List<ValidationRule<T>> rules = new ArrayList<>();

        public ValidationChain(T context) {
            this.context = context;
        }

        public ValidationChain<T> check(ValidationRule<T> rule) {
            this.rules.add(rule);
            return this;
        }

        public void validate() {
            for (ValidationRule<T> rule : rules) {
                rule.validate(context);
            }
        }
    }
}