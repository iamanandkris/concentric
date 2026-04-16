package io.concentric;

import io.concentric.annotations.*;

/**
 * Simulates the JVM bytecode emitted by a Kotlin data class that uses
 * plain parameter-site annotations (WITHOUT the {@code @field:} use-site
 * target).
 *
 * <pre>{@code
 * // Kotlin source equivalent:
 * @contract
 * data class KotlinUser(
 *     @immutable val id: Long,        // annotation lands on constructor param
 *     @nonEmpty  val name: String,    // annotation lands on constructor param
 *     @min(0)    val age: Int,        // annotation lands on constructor param
 *     @email     val email: String    // annotation lands on constructor param
 * )
 * }</pre>
 *
 * When Kotlin compiles the above WITHOUT {@code @field:} use-site target,
 * the annotations are written to the primary constructor parameters — NOT to
 * the backing JVM fields.  This class replicates that exact layout so that
 * {@link JvmContractDeriver}'s parameter-side annotation check
 * ({@code effectiveAnnotations(field, param)}) is exercised against real
 * bytecode, without requiring the Kotlin compiler in the test build.
 *
 * @see TestKotlinFieldUser for the {@code @field:} use-site variant.
 */
@contract
public class TestKotlinSimUser {

    // Backing fields — deliberately carry NO annotations (matching Kotlin output)
    private final long   id;
    private final String name;
    private final int    age;
    private final String email;

    // Primary constructor — annotations live HERE (matching kotlinc output)
    public TestKotlinSimUser(
        @immutable        long   id,
        @nonEmpty         String name,
        @min(0) @max(150) int    age,
        @email            String email
    ) {
        this.id    = id;
        this.name  = name;
        this.age   = age;
        this.email = email;
    }

    // Record-style accessors (matches what the test spec uses)
    public long   id()    { return id; }
    public String name()  { return name; }
    public int    age()   { return age; }
    public String email() { return email; }
}
