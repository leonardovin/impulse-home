package com.havalh6.viewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

public class TripExportTest {
    @Test
    public void numbersAreWhatASpreadsheetReads() {
        assertEquals("18", TripExport.csvField(18.0));
        assertEquals("16.2", TripExport.csvField(16.2));
        assertEquals("0.0001", TripExport.csvField(0.0001));
        assertEquals("-23.55052", TripExport.csvField(-23.55052));
        assertEquals("1726221600000", TripExport.csvField(1726221600000L));
        assertEquals("", TripExport.csvField(Double.NaN));
        assertEquals("", TripExport.csvField(null));
    }

    @Test
    public void textIsQuotedOnlyWhenItMustBe() {
        assertEquals("Centro", TripExport.csvField("Centro"));
        assertEquals("\"Rua A, 10\"", TripExport.csvField("Rua A, 10"));
        assertEquals("\"o \"\"bairro\"\"\"", TripExport.csvField("o \"bairro\""));
        assertEquals("\"a\nb\"", TripExport.csvField("a\nb"));
    }

    @Test
    public void aLineIsCommaSeparatedAndCrlfTerminated() {
        assertEquals("1,,\"x,y\",2.5\r\n", TripExport.csvLine(new Object[]{1L, null, "x,y", 2.5}));
    }

    @Test
    public void headerHasOneUniqueColumnPerField() {
        String header = TripExport.csvHeader();
        assertTrue(header.startsWith("start,end,startMs,endMs,"));
        assertTrue(header.endsWith("\r\n"));
        assertEquals(TripExport.CSV_COLUMNS.length,
                new HashSet<>(Arrays.asList(TripExport.CSV_COLUMNS)).size());
    }

    @Test
    public void fileStemSortsByTime() {
        String a = TripExport.fileStem(1_000_000_000_000L);
        String b = TripExport.fileStem(1_000_000_001_000L);
        assertTrue(a.matches("viagens-\\d{8}-\\d{6}"));
        assertTrue(a.compareTo(b) < 0);
    }
}
