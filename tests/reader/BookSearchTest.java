package com.inkreader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class BookSearchTest {
    public static void main(String[] args)throws Exception{
        Path file=Paths.get(args[0],"search-fixture.txt");Book book=new Book();book.id=42;book.filePath=file.toString();book.totalChapters=121;
        String text="中文前文 FindNeedle\n末尾🙂";byte[] bytes=text.getBytes(StandardCharsets.UTF_8);Files.write(file,bytes);
        try{
            ReaderDb db=new ReaderDb();for(int i=0;i<121;i++)db.segments.add(segment(i,i*37,bytes.length));
            AtomicInteger scanned=new AtomicInteger();List<SearchHit> hits=BookSearch.search(db,book,"findneedle",(percent,count)->scanned.set(count),new AtomicBoolean());
            check(hits.size()==121&&db.calls==2&&scanned.get()==121,"Search lost lazy second batch or progress");
            int offset=text.indexOf("FindNeedle");for(int i=0;i<hits.size();i++){
                SearchHit hit=hits.get(i);check(hit.chapterIndex==i&&hit.chapterOffset==i*37+offset,"UTF16/chapter search offset changed");check(hit.snippet.contains("FindNeedle")&&!hit.snippet.contains("\n"),"Search context did not preserve match and flatten newline");
            }
            ReaderDb cancelled=new ReaderDb();cancelled.segments.addAll(db.segments);check(BookSearch.search(cancelled,book,"needle",null,new AtomicBoolean(true)).isEmpty()&&cancelled.calls==0,"Initial cancellation read database/file segments");
            AtomicBoolean stop=new AtomicBoolean();ReaderDb during=new ReaderDb();during.segments.addAll(db.segments);hits=BookSearch.search(during,book,"needle",(percent,count)->stop.set(true),stop);check(hits.size()==120&&during.calls==1,"Cancellation after first batch did not stop lazy scan");
            StringBuilder repeated=new StringBuilder();for(int i=0;i<40;i++)repeated.append("needle ");Files.write(file,repeated.toString().getBytes(StandardCharsets.UTF_8));
            ReaderDb capped=new ReaderDb();for(int i=0;i<300;i++)capped.segments.add(segment(i,0,repeated.length()));hits=BookSearch.search(capped,book,"needle",null,new AtomicBoolean());check(hits.size()==BookSearch.MAX_RESULTS,"Search result cap not enforced");check(hits.get(11).chapterIndex==0&&hits.get(12).chapterIndex==1,"Per-segment cap not enforced");
            ReaderDb oversized=new ReaderDb();oversized.segments.add(segment(0,0,5*1024*1024));check(BookSearch.search(oversized,book,"needle",null,new AtomicBoolean()).isEmpty(),"Oversized segment read into memory");
            book.filePath="no-such-reader-fixture";check(BookSearch.search(new ReaderDb(),book,"  ",null,new AtomicBoolean()).isEmpty(),"Blank query attempted file IO");
        }finally{Files.deleteIfExists(file);}
        System.out.println("BookSearchTest PASS: UTF16 offsets, lazy batches, cancellation, bounded results/segments, local-only context");
    }
    private static SearchSegment segment(int chapter,int offset,long length){SearchSegment s=new SearchSegment();s.chapterIndex=chapter;s.chapterTitle="第"+chapter+"章";s.charStart=offset;s.byteStart=0;s.byteEnd=length;return s;}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
