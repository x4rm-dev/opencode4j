package io.opencode4j.config;

import lombok.experimental.UtilityClass;

/**
 * Shared connection defaults for local OpenCode and MCP server processes.
 */
@UtilityClass
public final class LocalServerDefaults {

    public static final String LOOPBACK_HOST = "127.0.0.1";
    public static final String HTTP_URL_PREFIX = "http://" + LOOPBACK_HOST + ":";
}
