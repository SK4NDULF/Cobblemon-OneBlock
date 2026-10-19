package io.github.sk4ndulf.cobblemon.oneblock.diagnostics;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticsTest {
    private final Diagnostics diagnostics = new Diagnostics(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    @Test
    void idContainsClassAndSixHexDigits() {
        Diagnostic d = diagnostics.report(ErrorClass.STORAGE, "boom");
        assertTrue(d.id().matches("OB-STORAGE-[0-9a-f]{6}"), d.id());
        assertEquals(ErrorClass.STORAGE, d.errorClass());
    }

    @Test
    void recentIsBoundedAndNewestFirst() {
        for (int i = 0; i < Diagnostics.MAX_RECENT + 10; i++) {
            diagnostics.report(ErrorClass.CONFIG, "n" + i);
        }
        assertEquals(Diagnostics.MAX_RECENT, diagnostics.recent().size());
        assertEquals("n" + (Diagnostics.MAX_RECENT + 9), diagnostics.recent().get(0).message());
    }
}
