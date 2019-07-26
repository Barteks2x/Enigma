package cuchaz.enigma.translation.mapping.serde;

import java.util.function.Predicate;

public class MappingsOption {
    private final String name;
    private final String description;
    private final String defaultValue;
    private final boolean required;
    private final Predicate<String> isValidValue;

    public MappingsOption(String name, String description, String defaultValue, boolean required, Predicate<String> isValidValue) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.required = required;
        this.isValidValue = isValidValue;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isValid(String value) {
        return isValidValue.test(value);
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MappingsOption that = (MappingsOption) o;
        return name.equals(that.name);
    }

    @Override public int hashCode() {
        return name.hashCode();
    }
}
