package dev.jason.dkv.acceptance.support;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestWatcher;

/** Resolves a {@code long seed} parameter and reports it when the test fails. */
public final class SeedExtension implements ParameterResolver, TestWatcher {

  public static final String PROPERTY = "seed";

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(SeedExtension.class);

  @Override
  public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
    Class<?> type = parameter.getParameter().getType();
    return type == long.class || type == Long.class;
  }

  @Override
  public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
    long seed = configuredSeed().orElseGet(() -> ThreadLocalRandom.current().nextLong());
    context.getStore(NAMESPACE).put(context.getUniqueId(), seed);
    return seed;
  }

  @Override
  public void testFailed(ExtensionContext context, Throwable cause) {
    Long seed = context.getStore(NAMESPACE).get(context.getUniqueId(), Long.class);
    if (seed == null) {
      return;
    }
    String test = context.getRequiredTestClass().getSimpleName();
    System.err.printf(
        "%n[seed] %s > %s failed with seed %d%n"
            + "[seed] rerun: ./mvnw verify -Dstage=%d -Dseed=%d -Dit.test=%s%n%n",
        test, context.getDisplayName(), seed, StageCondition.selectedStage(), seed, test);
  }

  static Optional<Long> configuredSeed() {
    String raw = System.getProperty(PROPERTY);
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(Long.parseLong(raw.trim()));
  }
}
