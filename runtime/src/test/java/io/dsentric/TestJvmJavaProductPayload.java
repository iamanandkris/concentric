package io.dsentric;

import io.dsentric.annotations.contract;
import io.dsentric.annotations.nonEmpty;

import java.util.List;

@contract
public record TestJvmJavaProductPayload(
    String sku,
    @nonEmpty String name,
    String description,
    String category,
    TestJvmJavaPrice price,
    TestJvmJavaInventory inventory,
    List<String> tags
) {}
