package org.nfcresearch.qpolllab;

/**
 * In-memory foreground rotation schedule. The caller supplies elapsed realtime,
 * stops this schedule when leaving the foreground, and performs the NFC write.
 * This class never retries a write, starts a thread, or persists a running job.
 */
public final class RotationSchedule {
    public static final int MAX_ITEMS = 30;
    public static final long MIN_INTERVAL_MILLIS = 60_000L;
    public static final long MAX_INTERVAL_MILLIS = 86_400_000L;

    /** A claim for exactly one write; only the ticket returned by claimDue is valid. */
    public static final class Ticket {
        public final long generation;
        public final int index;

        private Ticket(long generation, int index) {
            this.generation = generation;
            this.index = index;
        }
    }

    private long generation;
    private boolean running;
    private int itemCount;
    private int nextIndex = -1;
    private long intervalMillis;
    private long nextDueMillis = -1;
    private long lastNow;
    private Ticket activeTicket;

    /** Explicitly start a new session. The first item is immediately due. */
    public synchronized void start(int count, long interval, long now) {
        if (running || activeTicket != null) {
            throw new IllegalStateException("Rotation is already running");
        }
        if (count < 1 || count > MAX_ITEMS) {
            throw new IllegalArgumentException("Item count must be between 1 and " + MAX_ITEMS);
        }
        if (interval < MIN_INTERVAL_MILLIS || interval > MAX_INTERVAL_MILLIS) {
            throw new IllegalArgumentException("Interval must be between 1 minute and 24 hours");
        }
        checkTime(now);
        generation++;
        running = true;
        itemCount = count;
        intervalMillis = interval;
        nextIndex = 0;
        lastNow = now;
        nextDueMillis = now;
    }

    /**
     * Claim the due item or return null. Polling cannot overlap writes, and a late
     * poll claims only one item rather than replaying missed intervals.
     */
    public synchronized Ticket claimDue(long now) {
        if (!running || activeTicket != null) return null;
        long effectiveNow = monotonicNow(now);
        if (effectiveNow < nextDueMillis) return null;
        activeTicket = new Ticket(generation, nextIndex);
        nextDueMillis = -1;
        return activeTicket;
    }

    /**
     * Accept only the active claim. Success must mean the refresh was positively
     * confirmed by the protocol. Failure, cancellation, or an unconfirmed refresh
     * stops the session; callers must not replay the refresh command.
     *
     * The next interval begins at completion, avoiding overlapping NFC sessions.
     * Stale callbacks after stop/start are ignored without changing the clock.
     */
    public synchronized boolean complete(Ticket ticket, boolean success, long now) {
        if (!isCurrent(ticket)) return false;
        long effectiveNow = monotonicNow(now);
        if (!success) {
            stop();
            return true;
        }
        activeTicket = null;
        nextIndex = (nextIndex + 1) % itemCount;
        nextDueMillis = effectiveNow > Long.MAX_VALUE - intervalMillis
                ? Long.MAX_VALUE : effectiveNow + intervalMillis;
        return true;
    }

    /** Stop pending work and invalidate any completion callback. No implicit resume. */
    public synchronized void stop() {
        running = false;
        activeTicket = null;
        itemCount = 0;
        intervalMillis = 0;
        nextIndex = -1;
        nextDueMillis = -1;
    }

    public synchronized boolean isCurrent(Ticket ticket) {
        return running && ticket != null && ticket == activeTicket;
    }

    public synchronized boolean isRunning() { return running; }
    public synchronized boolean isInFlight() { return activeTicket != null; }

    /** Returns -1 while stopped or while a write is in flight. */
    public synchronized long nextDueMillis() { return nextDueMillis; }

    /** Returns -1 while stopped; while in flight returns that write's index. */
    public synchronized int nextIndex() { return nextIndex; }

    private long monotonicNow(long now) {
        checkTime(now);
        lastNow = Math.max(lastNow, now);
        return lastNow;
    }

    private static void checkTime(long now) {
        if (now < 0) throw new IllegalArgumentException("Elapsed realtime must be nonnegative");
    }
}
