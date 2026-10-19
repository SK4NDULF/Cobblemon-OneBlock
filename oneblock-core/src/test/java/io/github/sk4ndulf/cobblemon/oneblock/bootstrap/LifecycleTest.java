package io.github.sk4ndulf.cobblemon.oneblock.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleTest {
    @Test
    void startsInStartingAndOnlyReadyAllowsMutations() {
        Lifecycle lifecycle = new Lifecycle();
        assertEquals(LifecycleState.STARTING, lifecycle.state());
        for (LifecycleState s : LifecycleState.values()) {
            assertEquals(s == LifecycleState.READY, s.allowsMutations(), s.name());
        }
    }

    @Test
    void recoveryModeCannotBeLeftWithoutRestart() {
        Lifecycle lifecycle = new Lifecycle();
        assertTrue(lifecycle.transition(LifecycleState.RECOVERY_MODE, List.of()));
        assertFalse(lifecycle.transition(LifecycleState.READY, List.of()));
        assertFalse(lifecycle.transition(LifecycleState.SETUP_PENDING, List.of()));
        assertEquals(LifecycleState.RECOVERY_MODE, lifecycle.state());
        assertTrue(lifecycle.transition(LifecycleState.STOPPING, List.of()));
    }

    @Test
    void readOnlyIsNotTrustedAgainWithoutRestart() {
        Lifecycle lifecycle = new Lifecycle();
        lifecycle.transition(LifecycleState.READY, List.of());
        assertTrue(lifecycle.transition(LifecycleState.READ_ONLY, List.of()));
        assertFalse(lifecycle.transition(LifecycleState.READY, List.of()));
        assertTrue(lifecycle.transition(LifecycleState.RECOVERY_MODE, List.of()));
    }

    @ParameterizedTest
    @EnumSource(LifecycleState.class)
    void stoppingIsFinal(LifecycleState target) {
        Lifecycle lifecycle = new Lifecycle();
        lifecycle.transition(LifecycleState.STOPPING, List.of());
        assertEquals(target == LifecycleState.STOPPING, lifecycle.transition(target, List.of()));
        assertEquals(LifecycleState.STOPPING, lifecycle.state());
    }

    @Test
    void neverBackToStarting() {
        for (LifecycleState from : LifecycleState.values()) {
            assertFalse(Lifecycle.isAllowed(from, LifecycleState.STARTING), from.name());
        }
    }
}
