package io.opencode4j.client;

import io.opencode4j.OpenCodeException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The OpenCode server versions this library is tested against. OpenCode's HTTP API and event stream change
 * between minor versions, so a server outside the range may behave differently.
 */
public final class ServerCompatibility {

    /** Inclusive lower bound. */
    public static final String MINIMUM = "2.2.0";
    /** Exclusive upper bound. */
    public static final String BELOW = "2.3.0";

    private static final Pattern VERSION = Pattern.compile("v?(\\d+)\\.(\\d+)\\.(\\d+).*");

    /** What to do when the server is outside the tested range. */
    public enum Policy {
        /** Log a warning and continue. */
        WARN,
        /** Refuse to continue. */
        FAIL,
        /** Do not check. */
        IGNORE
    }

    private ServerCompatibility() {
    }

    /** Whether {@code version} (for example {@code 2.2.0} or {@code v2.2.1-beta}) is in the tested range. */
    public static boolean supports(String version) {
        int[] parsed = parse(version);
        return parsed != null && compare(parsed, parse(MINIMUM)) >= 0 && compare(parsed, parse(BELOW)) < 0;
    }

    /** A description of the tested range, for messages. */
    public static String range() {
        return ">=" + MINIMUM + " <" + BELOW;
    }

    /** Thrown under {@link Policy#FAIL} for a server outside the tested range. */
    public static final class IncompatibleServerException extends OpenCodeException {
        public IncompatibleServerException(String version) {
            super("OpenCode server " + version + " is outside the tested range " + range());
        }
    }

    private static int[] parse(String version) {
        if (version == null) {
            return null;
        }
        Matcher matcher = VERSION.matcher(version.trim());
        if (!matcher.matches()) {
            return null;
        }
        return new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))};
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }
}
