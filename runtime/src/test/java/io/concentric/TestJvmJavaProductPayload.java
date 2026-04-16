package io.concentric;

import io.concentric.annotations.contract;
import io.concentric.annotations.nonEmpty;

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
