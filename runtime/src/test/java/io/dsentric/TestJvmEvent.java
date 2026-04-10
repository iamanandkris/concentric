package io.dsentric;

import io.dsentric.annotations.*;

@contract
public record TestJvmEvent(
    @extract("\\d{4}-\\d{2}-\\d{2}") String date
) {}
