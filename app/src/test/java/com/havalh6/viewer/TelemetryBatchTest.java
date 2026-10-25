package com.havalh6.viewer;

import static org.junit.Assert.*;

import org.junit.Test;

public class TelemetryBatchTest {
    @Test
    public void arrivalsKeepTheirOrderAcrossKeysAndRepeatedValues() {
        TelemetryBatch batch = new TelemetryBatch();
        batch.add("door", "open");
        batch.add("speed", "10");
        batch.add("door", "closed");
        batch.add("door", "closed");
        assertEquals("if(window.onCarDataUpdate){"
                + "window.onCarDataUpdate('door','open');"
                + "window.onCarDataUpdate('speed','10');"
                + "window.onCarDataUpdate('door','closed');"
                + "window.onCarDataUpdate('door','closed');}", batch.drain());
    }

    @Test
    public void drainEmptiesTheBatchAndAllowsAnotherBatch() {
        TelemetryBatch batch = new TelemetryBatch();
        assertNull(batch.drain());
        batch.add("a", "1");
        assertNotNull(batch.drain());
        assertNull(batch.drain());
        batch.add("b", "2");
        assertEquals("if(window.onCarDataUpdate){window.onCarDataUpdate('b','2');}",
                batch.drain());
        assertNull(batch.drain());
    }

    @Test
    public void callsAreGuardedWhenThePageHasNoTelemetryHandler() {
        TelemetryBatch batch = new TelemetryBatch();
        batch.add("key", "value");
        assertEquals("if(window.onCarDataUpdate){window.onCarDataUpdate('key','value');}",
                batch.drain());
    }

    @Test
    public void bothArgumentsEscapeQuotesBackslashesAndLineTerminators() {
        TelemetryBatch batch = new TelemetryBatch();
        String tricky = "'\"\\\n\r\u2028\u2029\u0001";
        batch.add(tricky, tricky);
        String literal = "'\\'\\\"\\\\\\n\\r\\u2028\\u2029\\u0001'";
        assertEquals("if(window.onCarDataUpdate){window.onCarDataUpdate("
                + literal + "," + literal + ");}", batch.drain());
    }

    @Test
    public void trickyValueHasTheExactLiteralNeededToRecoverItsText() {
        TelemetryBatch batch = new TelemetryBatch();
        // Literal escape-looking text must survive alongside actual separators.
        batch.add("text", "C:\\new\\'quoted\"\nnext\r\u2028\u2029\tend");
        assertEquals("if(window.onCarDataUpdate){window.onCarDataUpdate('text',"
                + "'C:\\\\new\\\\\\'quoted\\\"\\nnext\\r\\u2028\\u2029\\u0009end');}",
                batch.drain());
    }
}
