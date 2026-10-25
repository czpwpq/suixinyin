package org.nfcresearch.qpolllab;

/** Executes only pre-framework path guards; deliberately never acquires a device lock on the host. */
public final class ReaderSessionLockTest {
    public static void main(String[] args) throws Exception {
        String owner = NfcSessionLock.path(10329).getPath().replace('\\', '/');
        String otherUser = NfcSessionLock.path(1010329).getPath().replace('\\', '/');
        check(owner.equals("/data/local/tmp/suixinyin-nfc-10329/session.lock"), "Unexpected shared session path");
        check(!owner.equals(otherUser), "Full Android UID was reduced to app ID");
        for (int uid : new int[]{Integer.MIN_VALUE, -1, 0, 2000, 9999}) {
            try { NfcSessionLock.path(uid); throw new AssertionError("Non-app UID accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        for (String invalid : new String[]{null, "", "relative/session.lock", "/data/local/tmp/session.lock",
                "/data/local/tmp/suixinyin-nfc-10329/../session.lock", "/data/local/tmp/suixinyin-nfc-010329/session.lock",
                "/data/local/tmp/suixinyin-nfc-0/session.lock", "/data/local/tmp/suixinyin-nfc-999999999999/session.lock",
                "/data/local/tmp/suixinyin-nfc-10329/session.lock/", "/data/local/tmp/suixinyin-nfc-10329/session.lock\n"}) {
            try { NfcSessionLock.acquire(invalid); throw new AssertionError("Unsafe lock path reached Android IO: " + invalid); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("ReaderSessionLockTest PASS: full UID isolation and invalid paths rejected before Android IO");
    }
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
}
