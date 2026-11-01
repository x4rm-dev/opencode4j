package io.opencode4j.client;

import java.util.regex.Pattern;

/** Turns request paths into low-cardinality route names for metrics. */
final class HttpRoutes {

    /** OpenCode ids look like {@code ses_e6d0...}, {@code msg_192f...}, {@code per_192f...}. */
    private static final Pattern ID = Pattern.compile("[a-z]{2,8}_[A-Za-z0-9]{6,}");

    private HttpRoutes() {
    }

    /** {@code /api/session/ses_123abc/message?limit=2} becomes {@code /api/session/{id}/message}. */
    static String template(String path) {
        int query = path.indexOf('?');
        String bare = query < 0 ? path : path.substring(0, query);
        if (bare.startsWith("/api/fs/read/")) {
            return "/api/fs/read/{path}";
        }
        StringBuilder route = new StringBuilder();
        for (String segment : bare.split("/", -1)) {
            if (!route.isEmpty() || !segment.isEmpty()) {
                route.append('/');
            }
            route.append(ID.matcher(segment).matches() ? "{id}" : segment);
        }
        String result = route.toString().replace("//", "/");
        return result.isEmpty() ? "/" : result;
    }
}
