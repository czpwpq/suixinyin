/* Adapted from miaomo src/dev/inknfclab/EditHistory.java at commit
 * 499268b34637a5c9e5199ef06e2eda4f9789870a. Original notices and
 * PolyForm Noncommercial 1.0.0 terms are retained in third-party/miaomo/. */
package org.nfcresearch.qpolllab;
import java.util.*;

/** Whole edit snapshots; raw source bitmap is never duplicated in undo history. */
final class EditHistory {
    private final ArrayDeque<PhotoEdit> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    void record(PhotoEdit state){undo.addLast(state.copy());while(undo.size()>30)undo.removeFirst();redo.clear();}
    boolean canUndo(){return !undo.isEmpty();}
    boolean canRedo(){return !redo.isEmpty();}
    PhotoEdit undo(PhotoEdit current){if(undo.isEmpty())return current.copy();redo.addLast(current.copy());return undo.removeLast().copy();}
    PhotoEdit redo(PhotoEdit current){if(redo.isEmpty())return current.copy();undo.addLast(current.copy());return redo.removeLast().copy();}
}
