package dev.jason.dkv.acceptance.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * For randomized tests. Declare a {@code long} parameter (conventionally named {@code seed}) on the
 * test method and it is injected: the value of {@code -Dseed} if given, otherwise a fresh random
 * seed. If the test fails, the seed and the exact rerun command are printed.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SeedExtension.class)
public @interface Seeded {}
