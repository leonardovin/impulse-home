package com.havalh6.viewer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * EXPORTAR VIAGENS: the file layout of a trip export, kept pure so the JVM
 * tests can pin it. {@link TripStore#exportTrips} fills it from the database.
 *
 * <p>Three files per export, written side by side under
 * {@code /sdcard/HavalH6Viewer/exports} (docs/features/energy-workspace.md):
 * {@code <stem>.json} holds everything recorded (every stored trip with its raw
 * trip-computer values, kept route, all its samples while they are kept, and its
 * refuel / charge stops, plus the daily totals), and {@code <stem>.csv} one row
 * per trip for a spreadsheet. Numbers keep the dot decimal; the CSV is RFC 4180
 * (comma, CRLF, quoted when needed). {@code <stem>.zip} carries both, for
 * ENVIAR POR BLUETOOTH (TripExportProvider): a phone takes a zip where it may
 * refuse a .json, and the JSON deflates to a fraction of its size over a slow link.
 */
final class TripExport {
    /** Bumped when a field changes meaning; adding a field does not bump it. */
    static final int FORMAT_VERSION = 1;
    static final String FORMAT = "impulse-trips";
    static final String DIR = "exports";

    /** CSV columns: the local start and end, then the trip's keys as the page reads them (TripStore.tripRow). */
    static final String[] CSV_COLUMNS = {
            "start", "end", "startMs", "endMs", "day", "startPlace", "endPlace",
            "km", "kmIntegrated", "odoStart", "odoEnd", "driveMs", "idleMs", "maxKmh",
            "evKm", "evMs", "evShare", "kwhOut", "kwhIn", "kwhPer100Km", "fuelL", "kmPerL",
            "fuelPctStart", "fuelPctEnd", "socStart", "socEnd",
            "stops", "harshAccel", "harshBrake", "climbM",
            "startLat", "startLon", "endLat", "endLon",
            "guided", "arrived", "plannedKm", "plannedS", "guidedKm", "guidedS", "detailKept",
    };

    private static final Pattern ZIP_NAME = Pattern.compile("viagens-\\d{8}-\\d{6}\\.zip");

    private TripExport() {}

    /** "1726221600000,1726308000000" from the page: the set of trips to export; unreadable entries are dropped. */
    static java.util.Set<Long> parseStarts(String list) {
        java.util.Set<Long> out = new java.util.LinkedHashSet<>();
        if (list == null) return out;
        for (String part : list.split(",")) {
            try {
                long v = Long.parseLong(part.trim());
                if (v > 0) out.add(v);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    /** An export zip's bare name, as fileStem makes it: what TripExportProvider may serve. */
    static boolean isZipName(String name) {
        return name != null && ZIP_NAME.matcher(name).matches();
    }

    /** {@code files} into {@code out}, each under its own name. */
    static void zip(File out, File... files) throws IOException {
        byte[] buf = new byte[64 * 1024];
        try (ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            for (File f : files) {
                z.putNextEntry(new ZipEntry(f.getName()));
                try (InputStream in = new FileInputStream(f)) {
                    for (int n; (n = in.read(buf)) > 0; ) z.write(buf, 0, n);
                }
                z.closeEntry();
            }
        }
    }

    /** {@code viagens-20261025-143005}: sorts by time, and two exports a second apart never collide. */
    static String fileStem(long nowMs) {
        return "viagens-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date(nowMs));
    }

    /** Wall-clock time for the CSV's start / end columns, in the head unit's zone. */
    static String localTime(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(ms));
    }

    static String csvHeader() {
        return csvLine(CSV_COLUMNS);
    }

    /** One CSV record, CRLF-terminated. A null is an empty field. */
    static String csvLine(Object[] values) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) b.append(',');
            b.append(csvField(values[i]));
        }
        return b.append("\r\n").toString();
    }

    static String csvField(Object v) {
        if (v == null) return "";
        String s;
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return "";
            // Whole values without ".0", others plain (never 1.0E-4): what a spreadsheet reads as a number.
            s = d == Math.rint(d) && Math.abs(d) < 1e15
                    ? Long.toString((long) d)
                    : new java.math.BigDecimal(d).round(new java.math.MathContext(10)).stripTrailingZeros().toPlainString();
        } else {
            s = String.valueOf(v);
        }
        boolean quote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        return quote ? '"' + s.replace("\"", "\"\"") + '"' : s;
    }
}
