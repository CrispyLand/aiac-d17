package com.crispyland.mcpserver.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Getting permission to read one Google Calendar, and nothing else.
 * <p>
 * This class is the whole of the OAuth story, on purpose. The tool that answers "what is on my
 * calendar" has no business knowing that a browser was opened, that a refresh token exists or
 * where it is kept; it receives an authorized {@link Calendar} and calls a method on it. Keeping
 * the seam here means the tool can be read, reviewed and reasoned about without OAuth in scope,
 * and it means the credential handling has exactly one home to audit.
 * <p>
 * <strong>Read-only is enforced by the scope, not by the tool.</strong> {@link #SCOPES} asks for
 * {@code calendar.readonly} alone, so the access token this produces is one Google will refuse to
 * let write. That matters because it is a guarantee that survives a mistake: if a future tool
 * method called {@code events.insert}, the request would fail at Google rather than succeed
 * quietly. A promise made in a code comment protects nothing; a scope does.
 * <p>
 * The credential is obtained eagerly, while the bean is created, which means a first run cannot
 * finish starting until consent is given in the browser. That is deliberate. The alternative —
 * authorizing lazily on the first tool call — moves a blocking browser prompt into the middle of
 * an MCP request that has a timeout on it, and leaves the agent's panel showing a healthy server
 * that cannot actually answer.
 */
@Configuration
@EnableConfigurationProperties(GoogleCalendarProperties.class)
public class GoogleCalendarAuth {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarAuth.class);

    /**
     * One scope, read-only. Widening this list is the only way this server could ever modify a
     * calendar, which makes the line worth guarding in review.
     */
    private static final List<String> SCOPES = List.of(CalendarScopes.CALENDAR_READONLY);

    /** Sent to Google as the caller's name; shows up on the consent screen and in audit logs. */
    private static final String APPLICATION_NAME = "crispyland-mcp-calendar";

    /**
     * Which user the cached credential belongs to. A constant because this server reads one
     * person's calendar — the one sitting at the machine when consent was given.
     */
    private static final String STORED_USER = "user";

    private final GoogleCalendarProperties properties;

    public GoogleCalendarAuth(GoogleCalendarProperties properties) {
        this.properties = properties;
    }

    /**
     * An authorized, read-only Calendar client.
     *
     * @throws IllegalStateException if the OAuth client file is missing, naming the absolute path
     *                               that was actually looked at — the default is relative, so
     *                               "not found" is nearly always a working-directory question and
     *                               printing the resolved path answers it in one line
     */
    @Bean
    public Calendar googleCalendar() throws IOException, GeneralSecurityException {
        Path credentials = properties.credentialsPath();
        if (!Files.isReadable(credentials)) {
            throw new IllegalStateException(
                    "Google OAuth client file not found at " + credentials + ". Download it from "
                            + "Google Cloud Console as an OAuth client of type 'Desktop app' and "
                            + "either put it there or set google.calendar.credentials-file.");
        }

        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        // Gson, not Jackson. google-api-client 2.9.1 depends on google-http-client-gson, so this
        // keeps Jackson 2 out of a Spring Boot 4 application that is otherwise on Jackson 3.
        JsonFactory json = GsonFactory.getDefaultInstance();

        GoogleClientSecrets secrets;
        try (Reader reader = Files.newBufferedReader(credentials, StandardCharsets.UTF_8)) {
            secrets = GoogleClientSecrets.load(json, reader);
        }

        Path tokens = properties.tokenPath();
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(transport, json, secrets, SCOPES)
                        .setDataStoreFactory(new FileDataStoreFactory(tokens.toFile()))
                        // Without this Google issues an access token and no refresh token, and
                        // the browser prompt comes back roughly an hour later. "Offline" is what
                        // makes consent a one-time event rather than an hourly one.
                        .setAccessType("offline")
                        .build();

        boolean firstRun = flow.loadCredential(STORED_USER) == null;
        if (firstRun) {
            log.info("No stored Google credential in {} — opening a browser for consent. "
                    + "Requesting {} only.", tokens, SCOPES);
        }

        // Port -1 asks the receiver for any free port. Desktop OAuth clients may redirect to any
        // loopback port, so pinning one would only invite a collision with nothing gained.
        LocalServerReceiver receiver =
                new LocalServerReceiver.Builder().setPort(properties.receiverPort()).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver)
                .authorize(STORED_USER);

        log.info("Google Calendar authorized read-only; credential cached in {}.", tokens);
        return new Calendar.Builder(transport, json, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
