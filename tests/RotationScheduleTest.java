package org.nfcresearch.qpolllab;

public final class RotationScheduleTest {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void rejects(Runnable operation, Class<? extends Exception> type) {
        try { operation.run(); }
        catch (Exception expected) {
            if (type.isInstance(expected)) return;
            throw new AssertionError("Wrong exception", expected);
        }
        throw new AssertionError("Expected " + type.getName());
    }

    public static void main(String[] args) throws InterruptedException {
        RotationSchedule schedule = new RotationSchedule();
        require(!schedule.isRunning() && !schedule.isInFlight(), "Initially stopped");
        require(schedule.claimDue(0) == null && schedule.nextIndex() == -1
                && schedule.nextDueMillis() == -1, "Stopped schedules cannot claim work");
        rejects(() -> schedule.start(0, 60_000, 0), IllegalArgumentException.class);
        rejects(() -> schedule.start(31, 60_000, 0), IllegalArgumentException.class);
        rejects(() -> schedule.start(1, 59_999, 0), IllegalArgumentException.class);
        rejects(() -> schedule.start(1, 86_400_001, 0), IllegalArgumentException.class);
        rejects(() -> schedule.start(1, 60_000, -1), IllegalArgumentException.class);
        require(!schedule.isRunning(), "Invalid requests must not start work");

        schedule.start(3, 60_000, 1_000);
        require(schedule.isRunning() && schedule.nextDueMillis() == 1_000, "First frame immediate");
        rejects(() -> schedule.start(1, 60_000, 1_000), IllegalStateException.class);
        RotationSchedule.Ticket first = schedule.claimDue(1_000);
        require(first != null && first.index == 0 && schedule.isCurrent(first), "Claim first");
        require(schedule.isInFlight() && schedule.nextDueMillis() == -1, "Claim consumes due timer");
        require(schedule.claimDue(999_999) == null, "Never overlap writes or catch up");
        require(schedule.complete(first, true, 11_000), "Complete first");
        require(!schedule.complete(first, true, 100_000), "Duplicate completion ignored");
        require(!schedule.isInFlight() && schedule.nextIndex() == 1
                && schedule.nextDueMillis() == 71_000, "Interval anchored to completion");
        require(schedule.claimDue(70_999) == null, "Not yet due");
        RotationSchedule.Ticket second = schedule.claimDue(71_000);
        require(second != null && second.index == 1 && second.generation == first.generation,
                "Second frame uses same generation");
        require(schedule.complete(second, true, 72_000), "Complete second");
        RotationSchedule.Ticket third = schedule.claimDue(132_000);
        require(third != null && third.index == 2, "Third frame");
        schedule.complete(third, true, 133_000);
        require(schedule.nextIndex() == 0, "Wrap to first frame");

        RotationSchedule.Ticket failed = schedule.claimDue(193_000);
        require(schedule.complete(failed, false, 194_000), "Accept failed completion");
        require(!schedule.isRunning() && !schedule.isInFlight()
                && schedule.nextDueMillis() == -1 && schedule.nextIndex() == -1,
                "Failure stops and clears all pending work");
        require(schedule.claimDue(999_999) == null, "No retry on failure");

        schedule.start(1, 60_000, 200_000);
        RotationSchedule.Ticket oldSession = schedule.claimDue(200_000);
        schedule.stop();
        require(!schedule.isCurrent(oldSession), "Stop invalidates ticket");
        require(!schedule.complete(oldSession, true, 201_000), "Stopped callback ignored");
        schedule.start(1, 60_000, 202_000);
        RotationSchedule.Ticket newSession = schedule.claimDue(202_000);
        require(newSession.generation != oldSession.generation, "Restart uses new generation");
        require(!schedule.complete(oldSession, false, Long.MAX_VALUE), "Old failure cannot stop new session");
        require(schedule.isCurrent(newSession) && schedule.isInFlight(), "Current ticket preserved");
        require(schedule.complete(newSession, true, 203_000), "New completion accepted");
        require(schedule.nextDueMillis() == 263_000, "Stale callback does not mutate clock");
        schedule.stop();
        require(schedule.claimDue(300_000) == null, "Stop cancels pending timer");

        schedule.start(1, 60_000, 400_000);
        require(schedule.claimDue(399_000) != null, "Clock regression clamps to start time");
        // Start a fresh session to test a regressing completion time.
        schedule.stop();
        schedule.start(1, 60_000, 400_000);
        RotationSchedule.Ticket clamped = schedule.claimDue(401_000);
        schedule.complete(clamped, true, 399_000);
        require(schedule.nextDueMillis() == 461_000, "Completion clock cannot move backward");
        require(schedule.claimDue(400_000) == null, "Regression does not prematurely advance frame");
        schedule.stop();

        schedule.start(30, 86_400_000, Long.MAX_VALUE - 10);
        RotationSchedule.Ticket nearOverflow = schedule.claimDue(Long.MAX_VALUE - 10);
        schedule.complete(nearOverflow, true, Long.MAX_VALUE - 5);
        require(schedule.nextDueMillis() == Long.MAX_VALUE, "Due time cannot overflow");
        require(schedule.claimDue(Long.MAX_VALUE - 1) == null, "Overflow must not cause an immediate write");
        schedule.stop();
        require(!new RotationSchedule().isRunning(), "New process never restarts previous session");

        // Timer, UI, and session callbacks may race. Only one can claim a frame.
        schedule.start(1, 60_000, 0);
        final RotationSchedule.Ticket[] claims = new RotationSchedule.Ticket[8];
        Thread[] threads = new Thread[claims.length];
        for (int i = 0; i < threads.length; i++) {
            final int slot = i;
            threads[i] = new Thread(() -> claims[slot] = schedule.claimDue(0));
            threads[i].start();
        }
        for (Thread thread : threads) thread.join();
        int claimCount = 0;
        for (RotationSchedule.Ticket ticket : claims) if (ticket != null) claimCount++;
        require(claimCount == 1 && schedule.isInFlight(), "Concurrent callers get exactly one claim");
        schedule.stop();
        System.out.println("RotationSchedule tests passed: bounds, single flight, completion intervals, wrap, failure stop, stale callbacks, clock regression");
    }
}
