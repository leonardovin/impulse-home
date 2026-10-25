package com.havalh6.viewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.zip.ZipInputStream;

public class TripExportTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

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

    @Test
    public void theProviderServesOnlyAnExportZipByName() {
        String stem = TripExport.fileStem(1_000_000_000_000L);
        assertTrue(TripExport.isZipName(stem + ".zip"));
        assertFalse(TripExport.isZipName(stem + ".json"));
        assertFalse(TripExport.isZipName("../" + stem + ".zip"));
        assertFalse(TripExport.isZipName("trips.db"));
        assertFalse(TripExport.isZipName(null));
    }

    @Test
    public void aSelectionIsTheStartTimesThePageSent() {
        assertEquals(new java.util.LinkedHashSet<>(Arrays.asList(1726221600000L, 1726308000000L)),
                TripExport.parseStarts(" 1726221600000,1726308000000,1726221600000"));
        assertEquals(new java.util.LinkedHashSet<>(Arrays.asList(5L)), TripExport.parseStarts("x,,5,-3,1e3"));
        assertTrue(TripExport.parseStarts("").isEmpty());
        assertTrue(TripExport.parseStarts(null).isEmpty());
    }

    @Test
    public void theZipCarriesBothFilesUnderTheirNames() throws Exception {
        File json = tmp.newFile("viagens-20261025-143005.json");
        File csv = tmp.newFile("viagens-20261025-143005.csv");
        Files.write(json.toPath(), "{\"trips\":[]}".getBytes(StandardCharsets.UTF_8));
        Files.write(csv.toPath(), "start,end\r\n".getBytes(StandardCharsets.UTF_8));
        File zip = new File(tmp.getRoot(), "viagens-20261025-143005.zip");
        TripExport.zip(zip, json, csv);
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip))) {
            assertEquals(json.getName(), in.getNextEntry().getName());
            assertEquals("{\"trips\":[]}", read(in));
            assertEquals(csv.getName(), in.getNextEntry().getName());
            assertEquals("start,end\r\n", read(in));
            assertNull(in.getNextEntry());
        }
    }

    private static String read(ZipInputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
