package com.crispyland.mcpserver;

import com.google.api.services.tasks.Tasks;
import com.google.api.services.tasks.model.Task;
import com.google.api.services.tasks.model.TaskList;
import com.google.api.services.tasks.model.TaskLists;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * What is still to do, from Google Tasks.
 * <p>
 * A sibling of {@link CalendarTools} and deliberately shaped like it: same authorized-service-in,
 * prose-out contract, same read-only guarantee inherited from the scopes rather than asserted
 * here. The two are separate classes because they are separate APIs — the Calendar UI displays
 * tasks beside events, which makes them look like one store, but {@code events.list} has never
 * returned a task. Merging them into one tool would mean one failing API silently truncating the
 * other's answer.
 * <p>
 * Nothing here knows about OAuth. The {@link Tasks} service arrives authorized from
 * {@code com.crispyland.mcpserver.google}, and the only thing this class can do with it is what
 * {@code tasks.readonly} permits.
 */
@Service
public class TaskTools {

    private static final Logger log = LoggerFactory.getLogger(TaskTools.class);

    /** Enough for any sane to-do list, and a bound so a pathological one cannot return forever. */
    private static final int MAX_TASKS = 50;

    private final Tasks tasks;

    public TaskTools(Tasks tasks) {
        this.tasks = tasks;
    }

    @McpTool(name = "getTasks",
            description = "Get my incomplete Google Tasks from the default task list. With a date, "
                    + "returns only tasks due on or before that day; without one, returns all "
                    + "outstanding tasks. Note that tasks are separate from calendar events — use "
                    + "getSchedule for meetings and appointments. Read-only: this cannot create, "
                    + "change or delete anything.")
    public String getTasks(
            @McpToolParam(description = "Optional cut-off day, as an ISO date in yyyy-MM-dd form, "
                    + "e.g. '2026-03-17'. Omit to list every outstanding task.",
                    required = false) String date) throws IOException {

        String dueBefore = null;
        LocalDate day = null;
        if (date != null && !date.isBlank()) {
            try {
                day = LocalDate.parse(date.strip());
            } catch (DateTimeParseException e) {
                // Returned rather than thrown. The model picked this string, so it is the one
                // party that can fix it, and it can only do that if the complaint names the format.
                return "'%s' is not a date I can read. Use ISO yyyy-MM-dd, for example 2026-03-17."
                        .formatted(date);
            }
            // The Tasks API stores a due date with the time discarded and the instant pinned to
            // midnight UTC — it is a date wearing a timestamp's clothes. So the bound is built
            // from the plain date, NOT by converting a local day to UTC the way getSchedule
            // correctly does for events. Doing that here would shift the cut-off by a day for
            // anyone not on UTC.
            //
            // The day AFTER the one asked for, because dueMax gets the same treatment on read as
            // due does on write: the time half is thrown away and what remains is compared
            // strictly. An end-of-day bound of `day + T23:59:59.999Z` therefore collapses back to
            // that day's midnight and excludes the tasks due on it — measured, not assumed: with
            // two tasks due 2026-09-24, a dueMax of 2026-09-24T23:59:59.999Z returned none and
            // 2026-09-25T23:59:59.999Z returned both. Half-open at midnight is also what the
            // comparison actually is, so it says what it means.
            dueBefore = day.plusDays(1) + "T00:00:00.000Z";
        }

        String listId = defaultListId();
        if (listId == null) {
            return "This Google account has no task lists.";
        }

        Tasks.TasksOperations.List request = tasks.tasks().list(listId)
                // Outstanding work only. showHidden stays false with it: a hidden task is one
                // already completed in Google's own clients, so asking for both would contradict
                // the question being answered.
                .setShowCompleted(false)
                .setShowHidden(false)
                .setMaxResults(MAX_TASKS);
        if (dueBefore != null) {
            request.setDueMax(dueBefore);
        }

        List<Task> items = request.execute().getItems();
        int found = (items == null) ? 0 : items.size();
        log.info("getTasks({}) -> {} incomplete task(s)", (day == null) ? "all" : day, found);

        if (found == 0) {
            return (day == null)
                    ? "No outstanding tasks."
                    : "No outstanding tasks due on or before " + day + ".";
        }

        StringBuilder out = new StringBuilder(256)
                .append(found == 1 ? "1 outstanding task" : found + " outstanding tasks");
        if (day != null) {
            out.append(" due on or before ").append(day);
        }
        out.append(":\n");
        for (Task task : items) {
            out.append("- ").append(describe(task)).append('\n');
        }
        // Said once, at the end, because a date filter silently drops undated tasks and a list
        // that looks complete but is not is worse than a longer one.
        if (day != null) {
            out.append("(Tasks with no due date are not included when filtering by date.)");
        }
        return out.toString().stripTrailing();
    }

    /**
     * The id of the account's default task list.
     * <p>
     * {@code tasklists().list()} returns the default first. The undocumented {@code @default}
     * alias would save this call and does work today, but it is absent from the REST reference
     * and the discovery document, which makes it a dependency on behaviour nobody promised.
     */
    private String defaultListId() throws IOException {
        TaskLists lists = tasks.tasklists().list().setMaxResults(1).execute();
        List<TaskList> items = lists.getItems();
        return (items == null || items.isEmpty()) ? null : items.get(0).getId();
    }

    /** One line per task: what it is, when it is due, and where it stands. */
    private String describe(Task task) {
        String title = (task.getTitle() == null || task.getTitle().isBlank())
                ? "(untitled)" : task.getTitle().strip();

        StringBuilder line = new StringBuilder(64).append(title);
        String due = dueDate(task);
        line.append(due == null ? " — no due date" : " — due " + due);

        // Always stated, even though this method only ever sees incomplete tasks. The status is
        // part of what was asked for, and a reader should not have to know how the query was
        // filtered to know what they are looking at.
        line.append(" [").append(completed(task) ? "completed" : "not completed").append(']');
        return line.toString();
    }

    /**
     * The due date as a plain date.
     * <p>
     * The field arrives as an RFC 3339 string whose time half is meaningless — Google discards it
     * on write. Truncating at the {@code T} reports what was actually stored instead of implying
     * a midnight deadline nobody set.
     */
    private String dueDate(Task task) {
        String due = task.getDue();
        if (due == null || due.isBlank()) {
            return null;
        }
        int t = due.indexOf('T');
        return (t < 0) ? due : due.substring(0, t);
    }

    private boolean completed(Task task) {
        return "completed".equals(task.getStatus());
    }
}
