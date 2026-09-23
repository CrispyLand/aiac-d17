package com.crispyland.mcpserver.google;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the OAuth files live and how the consent flow should behave.
 * <p>
 * Both paths default to the repository root — one directory above this server, which is where
 * Google Cloud Console's download lands and where the agent project's {@code .gitignore} already
 * covers them. They are properties rather than constants because both defaults are relative, and
 * a relative path is only as good as the working directory the process happens to start in.
 * <p>
 * They are held as {@code String} and converted here rather than declared as {@link Path}.
 * Spring's built-in {@code String → Path} conversion treats the value as a resource path and
 * normalizes it, which rejects a leading {@code ..} outright — the container refuses to start
 * with "has been normalized to [null]". {@link Path#of} has no such opinion, and a path that
 * climbs out of the working directory is exactly what is wanted here.
 *
 * @param credentialsFile the OAuth client downloaded from Google Cloud Console. Read, never
 *                        written. This is the application's identity, not the user's
 * @param tokenDirectory  where the granted credential is cached so consent is asked for once
 *                        rather than every start. This is the user's, and it is the file that
 *                        actually grants access to a calendar
 * @param receiverPort    the loopback port the browser is redirected back to after consent.
 *                        {@code -1} lets the receiver pick a free one, which is what a desktop
 *                        OAuth client is allowed to do — Google accepts any localhost port for
 *                        this client type, so pinning one buys nothing and can collide
 * @param timeZone        the zone that decides where a calendar day starts and ends. Empty means
 *                        the JVM's default, which is right when the server runs on the machine
 *                        whose schedule is being read
 */
@ConfigurationProperties(prefix = "google.calendar")
public record GoogleCalendarProperties(
        String credentialsFile,
        String tokenDirectory,
        int receiverPort,
        String timeZone) {

    public GoogleCalendarProperties {
        credentialsFile = blankTo(credentialsFile, "../credentials.json");
        tokenDirectory = blankTo(tokenDirectory, "../.google-tokens");
        receiverPort = (receiverPort == 0) ? -1 : receiverPort;
        timeZone = (timeZone == null) ? "" : timeZone.strip();
    }

    /** The OAuth client file, absolute — the only form worth naming in an error message. */
    public Path credentialsPath() {
        return Path.of(credentialsFile).toAbsolutePath().normalize();
    }

    /** The credential cache directory, absolute. */
    public Path tokenPath() {
        return Path.of(tokenDirectory).toAbsolutePath().normalize();
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.strip();
    }
}
