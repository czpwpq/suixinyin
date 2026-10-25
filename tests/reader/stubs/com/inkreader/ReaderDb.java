package com.inkreader;

import java.util.ArrayList;
import java.util.List;

/** Host-test stand-in for the SQLite boundary only. The search implementation is production code. */
public final class ReaderDb {
    public final List<SearchSegment> segments=new ArrayList<>();
    public int calls;
    public List<SearchSegment> searchSegments(long book,int offset,int limit){
        calls++;return new ArrayList<>(segments.subList(Math.min(offset,segments.size()),Math.min(offset+limit,segments.size())));
    }
    public boolean hashExists(String hash){throw new AssertionError("Host encoding test must not import a file");}
    public long insertImported(ImportData data,String path){throw new AssertionError("Host encoding test must not write SQLite");}
}
