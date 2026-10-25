package org.nfcresearch.qpolllab;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;

import com.inkreader.Book;
import com.inkreader.Chapter;
import com.inkreader.ChapterChunk;
import com.inkreader.EInkPageRenderer;
import com.inkreader.ReaderDb;
import com.inkreader.TxtImporter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/** Device-only import/layout checks. Uses isolated files, database and preferences; never binds NFC. */
public final class ReaderNativeChecks {
    private ReaderNativeChecks() { }

    public static void run(Instrumentation instrumentation) throws Exception {
        Context target = instrumentation.getTargetContext();
        File directory = new File(target.getCacheDir(), "reader-native-" + System.nanoTime());
        check(directory.mkdirs(), "Cannot create isolated reader test directory");
        IsolatedContext context = new IsolatedContext(target, directory);
        ReaderDb db = new ReaderDb(context);
        try {
            imports(context, db);
            pagination(context);
        } finally {
            db.close();
            context.deleteTestPreferences();
            removeTree(directory, directory.getCanonicalPath());
        }
    }

    private static void imports(IsolatedContext context, ReaderDb db) throws Exception {
        TxtImporter importer = new TxtImporter(context, db);
        TxtImporter.Progress progress = new TxtImporter.Progress() {
            public void update(int percent, String message) { }
            public boolean cancelled() { return false; }
        };
        String text = "作者：测试作者\r\n第一章 开始\r\n简体、繁體、English 123。\r\n第二章 继续\r\n这是结尾。";
        String normalized = text.replace("\r\n", "\n") + "\n";
        for (String encoding : new String[]{"UTF-8", "UTF-16LE", "UTF-16BE", "GB18030"}) {
            byte[] body = text.getBytes(Charset.forName(encoding));
            byte[] bom = "UTF-16LE".equals(encoding) ? new byte[]{(byte)255, (byte)254}
                    : "UTF-16BE".equals(encoding) ? new byte[]{(byte)254, (byte)255}
                    : "UTF-8".equals(encoding) ? new byte[]{(byte)239, (byte)187, (byte)191} : new byte[0];
            File source = write(context.root, "fixture-" + encoding + ".txt", join(bom, body));
            TxtImporter.Result result = importer.importUri(Uri.fromFile(source), progress);
            check(result.success, "Import failed for " + encoding + ": " + result.message);
            Book book = db.book(result.bookId);
            check(book != null && "测试作者".equals(book.author), "Author metadata missing: " + encoding);
            check(Arrays.equals(read(new File(book.filePath)), normalized.getBytes(StandardCharsets.UTF_8)),
                    "Decoded content differs: " + encoding);
            check(db.chapterCount(book.id) == 3, "Chapter boundaries differ: " + encoding);
            int count = db.listBooks(ReaderDb.SORT_RECENT).size();
            TxtImporter.Result duplicate = importer.importUri(Uri.fromFile(source), progress);
            check(duplicate.duplicate && db.listBooks(ReaderDb.SORT_RECENT).size() == count,
                    "Duplicate source created another book");
        }

        // Put a surrogate pair precisely across a 48 KiB chunk boundary.
        StringBuilder longLine = new StringBuilder();
        for (int i = 0; i < 49151; i++) longLine.append('甲');
        longLine.append("🙂");
        for (int i = 0; i < 62000; i++) longLine.append('乙');
        File large = write(context.root, "long-line.txt", longLine.toString().getBytes(StandardCharsets.UTF_8));
        TxtImporter.Result largeResult = importer.importUri(Uri.fromFile(large), progress);
        check(largeResult.success, "Long single-line TXT failed");
        Book book = db.book(largeResult.bookId);
        Chapter chapter = db.chapter(book.id, 0);
        check(chapter != null, "Long TXT chapter missing");
        ChapterChunk chunk = db.firstChunk(chapter.id);
        int chars = 0, chunks = 0;
        ByteArrayOutputStream rebuilt = new ByteArrayOutputStream();
        byte[] all = read(new File(book.filePath));
        while (chunk != null) {
            check(chunk.charStart == chars && chunk.charCount > 0 && chunk.charCount <= 49152,
                    "Chunk UTF-16 boundaries are unbounded or discontinuous");
            byte[] part = Arrays.copyOfRange(all, (int)chunk.byteStart, (int)chunk.byteEnd);
            String decoded = new String(part, StandardCharsets.UTF_8);
            check(decoded.length() == chunk.charCount && decoded.indexOf('\ufffd') < 0,
                    "Chunk split a Unicode scalar or lost characters");
            rebuilt.write(part); chars += chunk.charCount; chunks++;
            chunk = db.nextChunk(chapter.id, chunk.index);
        }
        check(chunks >= 3 && chars == longLine.length() + 1
                && Arrays.equals(all, rebuilt.toByteArray())
                && new String(all, StandardCharsets.UTF_8).equals(longLine + "\n"),
                "Long-line content was truncated or duplicated");

        int booksBefore = db.listBooks(ReaderDb.SORT_RECENT).size();
        int filesBefore = new File(context.getFilesDir(), "books").list().length;
        AtomicInteger polls = new AtomicInteger();
        File cancel = write(context.root, "cancel.txt", (longLine + "\nnot imported").getBytes(StandardCharsets.UTF_8));
        try {
            importer.importUri(Uri.fromFile(cancel), new TxtImporter.Progress() {
                public void update(int percent, String message) { }
                public boolean cancelled() { return polls.incrementAndGet() >= 3; }
            });
            throw new AssertionError("Cancelled import was committed");
        } catch (InterruptedException expected) { }
        check(booksBefore == db.listBooks(ReaderDb.SORT_RECENT).size()
                && filesBefore == new File(context.getFilesDir(), "books").list().length,
                "Cancelled import left a book or temporary file");
    }

    private static void pagination(IsolatedContext context) throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 500; i++) text.append("第").append(i).append("行：阅读分页必须连续，不能漏字、重叠或越界。\n");
        EInkPageRenderer renderer = new EInkPageRenderer();
        EInkPageRenderer.PageLayout layout = renderer.layout(context, 1, "原生分页检查", text.toString());
        check(layout.pageCount() > 2, "Fixture did not paginate");
        int end = 0;
        for (int page = 0; page < layout.pageCount(); page++) {
            check(layout.startChar(page) == end && layout.endChar(page) > end,
                    "Pages have a gap, overlap or empty page");
            end = layout.endChar(page);
            check(layout.pageAtChar(layout.startChar(page)) == page, "Saved position opens wrong page");
        }
        check(end == text.length() && layout.startChar(-1) == 0
                && layout.endChar(Integer.MAX_VALUE) == text.length(), "Pagination clamps are invalid");

        EInkPageRenderer.Snapshot a0 = renderer.render(context, layout, 0, "A0");
        EInkPageRenderer.Snapshot a1 = renderer.render(context, layout, 0, "A1");
        check(a0.packed.length == 105984 && a1.packed.length == 105984
                && a1.sha256.matches("[0-9a-f]{64}"), "Invalid reader frame or SHA");
        int[] first = PanelType.A0.preview(a0.packed), second = PanelType.A1.preview(a1.packed);
        // The footer clock may roll into another minute between the two renders.
        for (int pixel = 0; pixel < 552 * 720; pixel++)
            check(first[pixel] == second[pixel], "A0/A1 render orientation differs");
        int black = 0;
        for (int pixel : first) {
            check(pixel == Color.BLACK || pixel == Color.WHITE, "Reader text introduced dither or color");
            if (pixel == Color.BLACK) black++;
        }
        check(black > 1000, "Reader frame has no readable text");

        Bitmap phone = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888);
        try {
            phone.eraseColor(Color.GREEN);
            layout.drawPageText(new Canvas(phone), 1, Color.BLACK, 50, 70, 430, 650);
            for (int y = 0; y < 900; y++) for (int x = 0; x < 600; x++) {
                if (x < 50 || x >= 480 || y < 70 || y >= 720)
                    check(phone.getPixel(x, y) == Color.GREEN, "Phone reader drawing escaped its bounds");
            }
        } finally { phone.recycle(); }
    }

    private static final class IsolatedContext extends ContextWrapper {
        final File root;
        final String prefix;
        final java.util.ArrayList<String> preferenceNames = new java.util.ArrayList<>();
        IsolatedContext(Context base, File root) { super(base); this.root = root; prefix = root.getName() + "-"; }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getFilesDir() { File out = new File(root, "files"); out.mkdirs(); return out; }
        @Override public File getDatabasePath(String name) { File dir = new File(root, "databases"); dir.mkdirs(); return new File(dir, name); }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
        }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(), factory, handler);
        }
        @Override public boolean deleteDatabase(String name) { return SQLiteDatabase.deleteDatabase(getDatabasePath(name)); }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            String isolated = prefix + name;
            if (!preferenceNames.contains(isolated)) preferenceNames.add(isolated);
            return getBaseContext().getSharedPreferences(isolated, mode);
        }
        void deleteTestPreferences() { for (String name : preferenceNames) getBaseContext().deleteSharedPreferences(name); }
    }
    private static File write(File root, String name, byte[] bytes) throws Exception {
        File out = new File(root, name); try (FileOutputStream stream = new FileOutputStream(out)) { stream.write(bytes); } return out;
    }
    private static byte[] read(File file) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192];
        try (FileInputStream stream = new FileInputStream(file)) { int n; while ((n = stream.read(buffer)) >= 0) out.write(buffer, 0, n); } return out.toByteArray();
    }
    private static byte[] join(byte[] first, byte[] second) {
        byte[] out = Arrays.copyOf(first, first.length + second.length); System.arraycopy(second, 0, out, first.length, second.length); return out;
    }
    private static void removeTree(File file, String expectedRoot) throws Exception {
        String path = file.getCanonicalPath();
        check(path.equals(expectedRoot) || path.startsWith(expectedRoot + File.separator), "Unsafe fixture cleanup path");
        File[] children = file.listFiles(); if (children != null) for (File child : children) removeTree(child, expectedRoot);
        check(!file.exists() || file.delete(), "Cannot remove reader test fixture");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
