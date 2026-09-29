package dev.jason.dkv.acceptance.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks an acceptance test class or method as belonging to a stage. {@code ./mvnw verify -Dstage=N}
 * runs every test whose stage is at most N and reports the rest as skipped.
 *
 * <p>A method-level annotation overrides the class-level one.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(StageCondition.class)
public @interface Stage {
  int value();
}
