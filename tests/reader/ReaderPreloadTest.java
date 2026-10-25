package org.nfcresearch.qpolllab;

public final class ReaderPreloadTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        ReaderProtocolTest.Fake panel = new ReaderProtocolTest.Fake();
        ReaderPanelProtocol protocol = new ReaderPanelProtocol(panel, panel);
        protocol.prepare();
        check(panel.d1 == 2, "profile read and write-time recheck");
        check(panel.d3 == 0 && panel.d4 == 0, "prepare sends no image/refresh");

        byte[] page = new byte[ReaderPanelProtocol.IMAGE_BYTES];
        protocol.writeImage(page);
        check(panel.d3 > 0 && panel.d3 < 476, "one complete compressed frame preloaded");
        check(panel.d4 == 0, "preload must never refresh");
        int packets = panel.d3;

        protocol.refresh(2000, 500);
        check(panel.d3 == packets, "fast turn must not resend D3");
        check(panel.d4 == 1, "fast turn refreshes once");
        check(panel.emitted("REFRESH_DONE"), "busy to idle confirmed");
        System.out.println("ReaderPreloadTest: PASS (D3 preloaded, D4-only page turn)");
    }
}
