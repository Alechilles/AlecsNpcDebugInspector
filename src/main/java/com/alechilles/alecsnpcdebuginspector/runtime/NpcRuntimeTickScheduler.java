package com.alechilles.alecsnpcdebuginspector.runtime;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Advances a scenario one bounded observation step at a time.
 */
public final class NpcRuntimeTickScheduler {
    @Nonnull
    public RunSummary run(@Nonnull NpcRuntimeScenarioRun run,
                          @Nonnull StepExecutor executor,
                          @Nonnull TickStep tickStep) throws Exception {
        int ticksRun = 0;
        String cancelReason = null;
        String completionReason = null;
        for (int tick = 0; tick < run.deadlineTick(); tick++) {
            if (run.canceled()) {
                cancelReason = run.cancelReason();
                break;
            }

            run.setCurrentTick(tick);
            int stepTick = tick;
            TickOutcome outcome = executor.execute(() -> tickStep.run(stepTick));
            ticksRun++;

            if (outcome.canceled()) {
                cancelReason = outcome.cancelReason();
                if (!run.canceled()) {
                    run.cancel(cancelReason != null ? cancelReason : "canceled");
                }
                break;
            }
            if (outcome.completed()) {
                completionReason = outcome.completionReason();
                break;
            }
        }
        return new RunSummary(ticksRun, run.canceled(), cancelReason, completionReason != null, completionReason);
    }

    @FunctionalInterface
    public interface StepExecutor {
        @Nonnull
        TickOutcome execute(@Nonnull StepCallable step) throws Exception;

        @Nonnull
        static StepExecutor direct() {
            return StepCallable::run;
        }
    }

    @FunctionalInterface
    public interface StepCallable {
        @Nonnull
        TickOutcome run() throws Exception;
    }

    @FunctionalInterface
    public interface TickStep {
        @Nonnull
        TickOutcome run(int tick) throws Exception;
    }

    public record TickOutcome(boolean canceled,
                              @Nullable String cancelReason,
                              boolean completed,
                              @Nullable String completionReason) {
        @Nonnull
        public static TickOutcome continueRunning() {
            return new TickOutcome(false, null, false, null);
        }

        @Nonnull
        public static TickOutcome canceled(@Nonnull String reason) {
            return new TickOutcome(true, reason, false, null);
        }

        @Nonnull
        public static TickOutcome completed(@Nonnull String reason) {
            return new TickOutcome(false, null, true, reason);
        }
    }

    public record RunSummary(int ticksRun,
                             boolean canceled,
                             @Nullable String cancelReason,
                             boolean completedEarly,
                             @Nullable String completionReason) {
    }
}
