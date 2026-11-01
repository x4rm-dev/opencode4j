package io.opencode4j.event;

/** The server disposed a location's services, for example after an eviction. */
public record LocationShutdown(
        String directory
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "location.shutdown";
    }
}
