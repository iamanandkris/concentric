package io.concentric;

import io.concentric.annotations.FieldValidator;
import java.util.List;

public class TestJvmPhoneValidator implements FieldValidator<String> {
    @Override
    public List<String> validate(String value) {
        return value != null && value.matches("\\+[1-9]\\d{6,14}")
            ? List.of()
            : List.of("invalid phone");
    }
}
