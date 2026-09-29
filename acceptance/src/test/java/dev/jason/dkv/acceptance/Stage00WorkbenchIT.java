package dev.jason.dkv.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.jason.dkv.acceptance.support.SeedExtension;
import dev.jason.dkv.acceptance.support.Seeded;
import dev.jason.dkv.acceptance.support.Stage;
import dev.jason.dkv.acceptance.support.StageCondition;
import org.junit.jupiter.api.Test;

/** Stage 0: the test loop itself is trustworthy. */
@Stage(0)
class Stage00WorkbenchIT {

  @Test
  void stageSystemPropertyReachesTheTestJvm() {
    int stage = StageCondition.selectedStage();
    assertTrue(stage >= 0, "-Dstage must be a non-negative stage number, got " + stage);
  }

  @Test
  void testsRunOnJava21OrLater() {
    assertTrue(
        Runtime.version().feature() >= 21,
        "acceptance tests must run on Java 21+, got " + Runtime.version());
  }

  @Test
  @Seeded
  void seedFromCommandLineIsReplayedExactly(long seed) {
    String configured = System.getProperty(SeedExtension.PROPERTY);
    if (configured != null && !configured.isBlank()) {
      assertEquals(Long.parseLong(configured.trim()), seed, "-Dseed must be injected unchanged");
    }
  }

  @Test
  @Stage(Integer.MAX_VALUE)
  void testsAboveTheSelectedStageAreSkipped() {
    fail("stage selection is broken: a test above -Dstage ran");
  }
}
