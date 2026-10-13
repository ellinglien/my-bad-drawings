package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Ordered pages; only two recently used tone documents and their undo histories stay decoded. */
final class DrawingBook {
    static final int MAX_PAGES=100, MAX_PACKED_BYTES=16*1024*1024;
    final int width,height;
    private final ArrayList<byte[]> pages=new ArrayList<>();
    private final LinkedHashMap<Integer,ToneDocument> cache=new LinkedHashMap<>(4,.75f,true);
    private final java.util.HashMap<Integer,Long> packedRevisions=new java.util.HashMap<>();
    // Unsaved changes: book-level edits (pages, canvas choices, edited pages leaving the cache)
    // plus each decoded page's revision as of the last save or load.
    private long edits,savedEdits;
    private final java.util.HashMap<Integer,Long> savedRevisions=new java.util.HashMap<>();
    private int index;
    private final ArrayList<Integer> canvasChoices=new ArrayList<>();
    int canvasChoice() { return canvasChoices.get(index); }
    void setCanvasChoice(int choice) {
        if(choice<0 || choice>2)throw new IllegalArgumentException("Invalid canvas choice");
        if(canvasChoices.set(index,choice)!=choice)edits++;
    }

    DrawingBook(ToneDocument first) {
        width=first.width;height=first.height;pages.add(null);canvasChoices.add(0);cache.put(0,first);
        savedRevisions.put(0,first.revision());
    }
    DrawingBook(int width,int height,ArrayList<byte[]> pages,int index) throws IOException {
        this(width,height,pages,index,null);
    }
    DrawingBook(int width,int height,ArrayList<byte[]> pages,int index,ToneDocument decoded) throws IOException {
        ToneDocument.validateSize(width,height);this.width=width;this.height=height;
        if(pages.isEmpty()||pages.size()>MAX_PAGES||index<0||index>=pages.size())throw new IOException("Invalid page list");
        this.pages.addAll(pages); for(int i=0;i<pages.size();i++)canvasChoices.add(0); this.index=index;cache.put(index,decoded==null?decode(pages.get(index)):decoded);
        packedRevisions.put(index,current().revision());savedRevisions.put(index,current().revision());
    }
    int count(){return pages.size();}
    int index(){return index;}
    ToneDocument current(){return cache.get(index);}
    void select(int target) throws IOException {
        if(target<0||target>=count())throw new IllegalArgumentException("Invalid page");
        if(target==index)return;
        retainCurrent();
        ToneDocument next=cache.get(target);
        if(next==null) {next=decode(pages.get(target));packedRevisions.put(target,next.revision());savedRevisions.put(target,next.revision());}
        index=target;cache.put(index,next);evict();
    }
    void addPage() throws IOException {
        if(count()>=MAX_PAGES)throw new IOException("This painting has 100 pages. Start a new painting for more pages.");
        retainCurrent();
        ToneDocument next=new ToneDocument(width,height);
        index=pages.size();pages.add(null);canvasChoices.add(0);cache.put(index,next);edits++;evict();
    }
    /** Inserts a copy of the current page right after it, without its undo history, and selects the copy. */
    void duplicatePage() throws IOException {
        if(count()>=MAX_PAGES)throw new IOException("This painting has 100 pages. Start a new painting for more pages.");
        retainCurrent();
        byte[] packed=currentPacked();long size=packed.length;
        for(byte[] page:pages)if(page!=null)size+=page.length;
        if(size>MAX_PACKED_BYTES)throw new IOException("This painting is full. Save it and start a new painting.");
        ToneDocument copy=decode(packed);
        int target=index+1;
        shiftPages(target,1);
        pages.add(target,packed);canvasChoices.add(target,canvasChoice());
        index=target;cache.put(index,copy);packedRevisions.put(index,copy.revision());edits++;evict();
    }
    /** Removes the current page and selects the page after it, or the one before when it was last. */
    void deletePage() throws IOException {
        if(count()<=1)throw new IOException("A painting keeps at least one page.");
        int removed=index,next=removed+1<count()?removed+1:removed-1;
        // Decode the page to show before changing anything, so a damaged page leaves the book whole.
        ToneDocument shown=cache.get(next);
        if(shown==null) {shown=decode(pages.get(next));packedRevisions.put(next,shown.revision());savedRevisions.put(next,shown.revision());}
        cache.remove(removed);packedRevisions.remove(removed);savedRevisions.remove(removed);
        pages.remove(removed);canvasChoices.remove(removed);
        shiftPages(removed+1,-1);
        index=Math.min(next,removed);cache.put(index,shown);edits++;evict();
    }
    /** Moves the cached documents and revisions of pages from {@code first} on by {@code by}. */
    private void shiftPages(int first,int by) {
        LinkedHashMap<Integer,ToneDocument> shifted=new LinkedHashMap<>(4,.75f,true);
        for(java.util.Map.Entry<Integer,ToneDocument> page:cache.entrySet())shifted.put(shift(page.getKey(),first,by),page.getValue());
        cache.clear();cache.putAll(shifted);
        shiftKeys(packedRevisions,first,by);shiftKeys(savedRevisions,first,by);
    }
    private static int shift(int page,int first,int by){return page>=first?page+by:page;}
    private static void shiftKeys(java.util.HashMap<Integer,Long> map,int first,int by) {
        java.util.HashMap<Integer,Long> shifted=new java.util.HashMap<>();
        for(java.util.Map.Entry<Integer,Long> page:map.entrySet())shifted.put(shift(page.getKey(),first,by),page.getValue());
        map.clear();map.putAll(shifted);
    }
    private void retainCurrent() throws IOException {
        // Browsing does not change a page. Keep its already-compressed bytes,
        // including after decoding an evicted page, instead of deflating it again.
        if(currentPacked()!=null)return;
        byte[] packed=encode(current());long size=packed.length;
        for(int i=0;i<pages.size();i++)if(i!=index&&pages.get(i)!=null)size+=pages.get(i).length;
        if(size>MAX_PACKED_BYTES)throw new IOException("This painting is full. Save it and start a new painting.");
        pages.set(index,packed);packedRevisions.put(index,current().revision());current().trimHistory(8*1024*1024);
    }
    private byte[] currentPacked() {
        Long revision=packedRevisions.get(index);
        return revision!=null && revision==current().revision()?pages.get(index):null;
    }
    private void evict(){
        long pixels=0; for(ToneDocument page:cache.values()) pixels+=(long)page.width*page.height*page.layerCount()*2;
        while(cache.size()>1&&(cache.size()>2||pixels>32*1024*1024)) {
            int removedIndex=cache.keySet().iterator().next();
            ToneDocument removed=cache.remove(removedIndex);packedRevisions.remove(removedIndex);
            if(!Long.valueOf(removed.revision()).equals(savedRevisions.remove(removedIndex)))edits++;
            pixels-=(long)removed.width*removed.height*removed.layerCount()*2;
        }
    }
    private ToneDocument decode(byte[] packed) throws IOException {
        ToneDocument document=DocumentCodec.read(new ByteArrayInputStream(packed));
        return document;
    }
    static byte[] encode(ToneDocument document) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        DocumentCodec.write(out,document.layerSnapshot()); return out.toByteArray();
    }
    static byte[] encode(int width,int height,byte[] tones) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();DocumentCodec.write(out,width,height,tones);return out.toByteArray();
    }
    void restoreCanvasChoices(int[] choices) {
        if(choices.length!=count())throw new IllegalArgumentException("Invalid canvas choices");
        for(int i=0;i<choices.length;i++)canvasChoices.set(i,choices[i]);
    }
    /** Whether anything changed since this book was loaded, created blank, or last {@link #markSaved saved}. */
    boolean unsaved() {
        if(edits!=savedEdits)return true;
        for(java.util.Map.Entry<Integer,ToneDocument> page:cache.entrySet())
            if(!Long.valueOf(page.getValue().revision()).equals(savedRevisions.get(page.getKey())))return true;
        return false;
    }
    /** Records that {@code saved}, taken from this book, reached its file; later edits stay unsaved. */
    void markSaved(Snapshot saved) {
        savedEdits=saved.edits; savedRevisions.clear(); savedRevisions.putAll(saved.revisions);
    }
    /** For a recovered book whose changes were never saved. */
    void markUnsaved() { edits++; }
    Snapshot snapshot(){return new Snapshot(this);}
    static final class Snapshot {
        final int width,height,index;
        final ToneDocument.Snapshot activePage;
        final byte[] packedActivePage;
        final ArrayList<byte[]> pages;
        final ArrayList<Integer> canvasChoices;
        final boolean unsaved;
        private final long edits;
        private final java.util.HashMap<Integer,Long> revisions=new java.util.HashMap<>();
        Snapshot(DrawingBook book){
            unsaved=book.unsaved(); edits=book.edits;
            for(java.util.Map.Entry<Integer,ToneDocument> page:book.cache.entrySet())revisions.put(page.getKey(),page.getValue().revision());
            width=book.width;height=book.height;index=book.index;pages=new ArrayList<>(book.pages);
            canvasChoices=new ArrayList<>(book.canvasChoices);
            activePage=book.current().layerSnapshot();
            packedActivePage=book.currentPacked();
        }
        int count(){return pages.size();}
        /** Decodes one page without selecting it or touching the live book's undo history. */
        ToneDocument page(int i) throws IOException {
            if(i==index)return new ToneDocument(activePage.width,activePage.height,activePage.layers,activePage.active,activePage.mask);
            return DocumentCodec.read(new ByteArrayInputStream(pages.get(i)));
        }
    }
}
