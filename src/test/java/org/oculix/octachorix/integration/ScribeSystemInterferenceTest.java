/*
 * Octachorix — deterministic Tesseract runtime for the JVM.
 * Copyright (C) 2026 Julien Mer
 */
package org.oculix.octachorix.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.oculix.octachorix.Scribe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The theorem the whole project exists to prove.
 *
 * <p>Setup: a host with a system Tesseract installed next to ours,
 * {@code /usr/lib/x86_64-linux-gnu/libtesseract.so.5.0.3} from apt on
 * Linux or {@code /opt/homebrew/lib/libtesseract.5.dylib} from Homebrew
 * on macOS, plus a bundled Tesseract (typically provisioned by Legerix
 * into {@code ~/.cache/legerix/...}) whose absolute path we hand to
 * {@link Scribe.Builder#tesseractLibrary(Path)}.
 *
 * <p>Claim: only the bundled library is used. The system one, however
 * seductive to JNA's {@code matchLibrary()} version-max heuristic, is
 * never loaded and never queried by Octachorix.
 *
 * <p>Proof: {@link Scribe#tesseractVersion()} returns the version
 * string of the actually-loaded library. We compare it against the
 * expected version passed via
 * {@code -Doctachorix.test.expectedVersion=5.5.0}. If Octachorix ever
 * regressed to short-name resolution, this test would report the
 * system's {@code 5.3.4} (or whatever apt shipped) instead of our
 * bundled {@code 5.5.0}, and fail loud.
 *
 * <h2>How to run</h2>
 * <pre>
 * mvn test \
 *   -Doctachorix.test.libs.tesseract=/home/x/.cache/legerix/.../libtesseract.so.5 \
 *   -Doctachorix.test.libs.leptonica=/home/x/.cache/legerix/.../libleptonica.so.6 \
 *   -Doctachorix.test.libs.tessdata=/home/x/.cache/legerix/.../tessdata \
 *   -Doctachorix.test.expectedVersion=5.5.0
 * </pre>
 *
 * <p>The test is skipped when no system Tesseract is found in the
 * usual apt or Homebrew locations (there is nothing to interfere
 * with). On Linux the bundled library wins because {@code preload()}
 * opens Leptonica with {@code RTLD_GLOBAL}; on macOS it wins because
 * the bundled dylibs carry {@code @loader_path} install names. Both
 * mechanisms are proved by the same assertion.
 */
class ScribeSystemInterferenceTest {

    private static Path tesseractLib;
    private static Path leptonicaLib;
    private static Path tessdata;
    private static String expectedVersion;

    @BeforeAll
    static void loadPathsOrSkip() {
        String t = System.getProperty("octachorix.test.libs.tesseract");
        String l = System.getProperty("octachorix.test.libs.leptonica");
        String d = System.getProperty("octachorix.test.libs.tessdata");
        String v = System.getProperty("octachorix.test.expectedVersion");

        assumeTrue(t != null && l != null && d != null && v != null,
                () -> "System-interference test skipped — set "
                        + "-Doctachorix.test.libs.{tesseract,leptonica,tessdata} "
                        + "AND -Doctachorix.test.expectedVersion=<bundled version> "
                        + "to enable it.");

        assumeTrue(systemTesseractPresent(),
                () -> "System-interference test skipped — no system Tesseract "
                        + "found in /usr/lib*, /usr/local/lib or /opt/homebrew/lib. "
                        + "Nothing to interfere with, the theorem holds vacuously.");

        tesseractLib = Paths.get(t);
        leptonicaLib = Paths.get(l);
        tessdata = Paths.get(d);
        expectedVersion = v;
    }

    @Test
    void loadedLibraryIsTheBundledOneNotTheSystemOne() {
        try (Scribe scribe = Scribe.builder()
                .tesseractLibrary(tesseractLib)
                .leptonicaLibrary(leptonicaLib)
                .datapath(tessdata)
                .language("eng")
                .build()) {

            String actualVersion = scribe.tesseractVersion();
            assertNotNull(actualVersion,
                    "TessVersion() should not return null");

            // Exact-string comparison. If the system's tesseract sneaked
            // in, its version would differ from the one we asked for and
            // this assertion would fail with a very legible message.
            assertEquals(expectedVersion, actualVersion,
                    "Loaded Tesseract version does not match the bundled "
                    + "one. If a system Tesseract is present, this is the "
                    + "smoking gun of short-name resolution stealing the show. "
                    + "Expected bundled version: " + expectedVersion
                    + " — actually got: " + actualVersion);
        }
    }

    private static boolean systemTesseractPresent() {
        String[] candidates = {
                "/usr/lib/x86_64-linux-gnu/libtesseract.so.5",
                "/usr/lib/x86_64-linux-gnu/libtesseract.so.4",
                "/usr/lib/aarch64-linux-gnu/libtesseract.so.5",
                "/usr/lib/aarch64-linux-gnu/libtesseract.so.4",
                "/usr/lib64/libtesseract.so.5",
                "/usr/lib64/libtesseract.so.4",
                "/usr/lib/libtesseract.so.5",
                "/usr/lib/libtesseract.so.4",
                "/usr/local/lib/libtesseract.so.5",
                "/usr/local/lib/libtesseract.so.4",
                "/opt/homebrew/lib/libtesseract.5.dylib",
                "/usr/local/lib/libtesseract.5.dylib",
        };
        for (String c : candidates) {
            if (Files.exists(Paths.get(c))) {
                return true;
            }
        }
        return false;
    }
}
