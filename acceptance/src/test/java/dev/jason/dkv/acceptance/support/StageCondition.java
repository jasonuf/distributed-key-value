package dev.jason.dkv.acceptance.support;

import java.util.Optional;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

/** Disables tests whose {@link Stage} is above the {@code stage} system property. */
public final class StageCondition implements ExecutionCondition {

  public static final String PROPERTY = "stage";

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    Optional<Stage> stage =
        context
            .getElement()
            .flatMap(element -> AnnotationSupport.findAnnotation(element, Stage.class));
    if (stage.isEmpty()) {
      return ConditionEvaluationResult.enabled("no @Stage");
    }
    int selected = selectedStage();
    int required = stage.get().value();
    return required <= selected
        ? ConditionEvaluationResult.enabled("stage " + required + " <= " + selected)
        : ConditionEvaluationResult.disabled(
            "stage " + required + " not selected (running stages 0.." + selected + ")");
  }

  /** The selected stage. Fails loudly rather than guessing if the property is missing. */
  public static int selectedStage() {
    String raw = System.getProperty(PROPERTY);
    if (raw == null || raw.isBlank()) {
      throw new IllegalStateException(
          "System property 'stage' is not set in the test JVM; check Failsafe's"
              + " systemPropertyVariables in acceptance/pom.xml");
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      throw new IllegalStateException("System property 'stage' is not an integer: " + raw, e);
    }
  }
}
