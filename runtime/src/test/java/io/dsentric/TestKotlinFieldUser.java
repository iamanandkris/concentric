package io.dsentric;

import io.dsentric.annotations.*;

/**
 * Simulates the JVM bytecode emitted by a Kotlin data class that uses the
 * {@code @field:} use-site target on every annotation.
 *
 * <pre>{@code
 * // Kotlin source equivalent:
 * @contract
 * data class KotlinUser(
 *     @field:immutable val id: Long,
 *     @field:nonEmpty  val name: String,
 *     @field:min(0)    val age: Int,
 *     @field:email     val email: String
 * )
 * }</pre>
 *
 * With {@code @field:}, the Kotlin compiler writes annotations to the
 * backing JVM field (as Java would) rather than to the constructor
 * parameter.  This class replicates that layout to exercise the field-side
 * annotation check in {@link JvmContractDeriver}.
 *
 * @see TestKotlinSimUser for the parameter-site annotation variant.
 */
@contract
public class TestKotlinFieldUser {

    // Backing fields — annotations live HERE (matching @field: Kotlin output)
    @immutable        private final long   id;
    @nonEmpty         private final String name;
    @min(0) @max(150) private final int    age;
    @email            private final String email;

    // Primary constructor — deliberately carries NO annotations
    public TestKotlinFieldUser(long id, String name, int age, String email) {
        this.id    = id;
        this.name  = name;
        this.age   = age;
        this.email = email;
    }

    public long   id()    { return id; }
    public String name()  { return name; }
    public int    age()   { return age; }
    public String email() { return email; }
}
