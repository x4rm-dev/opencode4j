package io.opencode4j.model.form;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.ArrayList;
import java.util.List;

/**
 * A scalar or string-list answer supplied to an integration form.
 *
 * <p>On the wire every variant is a bare JSON value: a string, number, boolean or array of strings.</p>
 */
public sealed interface FormValue permits StringValue, NumberValue, BooleanValue, StringListValue {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static FormValue fromJson(Object value) {
        if (value instanceof String string) {
            return of(string);
        }
        if (value instanceof Number number) {
            return of(number.doubleValue());
        }
        if (value instanceof Boolean bool) {
            return of(bool.booleanValue());
        }
        if (value instanceof List<?> values) {
            return of(toStrings(values));
        }
        throw new IllegalArgumentException("Unsupported form value type: " + describe(value));
    }

    static FormValue of(String value) {
        return new StringValue(value);
    }

    static FormValue of(double value) {
        return new NumberValue(value);
    }

    static FormValue of(boolean value) {
        return new BooleanValue(value);
    }

    static FormValue of(List<String> value) {
        return new StringListValue(value);
    }

    private static List<String> toStrings(List<?> values) {
        List<String> strings = new ArrayList<>(values.size());
        for (Object element : values) {
            if (!(element instanceof String string)) {
                throw new IllegalArgumentException(
                        "Form list values must be strings, found: " + describe(element));
            }
            strings.add(string);
        }
        return strings;
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName() + " (" + value + ")";
    }
}
