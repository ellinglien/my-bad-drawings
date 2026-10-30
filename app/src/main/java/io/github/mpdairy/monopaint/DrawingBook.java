package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Ordered pages; only two recently used tone documents stay decoded, and only the current one keeps its undo. A page's packed bytes are
 * a {@link PackedPage}: read from the book's file when needed, held in memory only once made or changed since the book
 * was saved (Inkwash's notebooks, 2026-10-18), so a book may hold far more than fits in memory.
 *
 * <p>Inkwash: a notebook's front and back covers ({@link #FRONT}, {@link #BACK}; docs/archive/superpowers/specs/
 * 2026-10-19-notebook-options-design.md, "Covers") are drawn on like pages, before the first page and after the last,
 * but aren't counted among them ({@link #count}), copied or deleted. Their paper is made from the book's {@link #name}
 * and its {@link #design} ({@link NotebookCovers}) when they are decoded, under what is drawn on them; one with nothing
 * drawn has no bytes.
 */
final class DrawingBook {
    /**
     * At most 250 pages (100 until the owner, 2026-10-22, "let's increase to another max pg count": a PDF's pages take two
     * each with their drawing layer), and 200 MB of them on disk (16 MB while every page was held in memory). Older
     * builds refuse a notebook of more than 100.
     */
    static final int MAX_PAGES=250, MAX_PACKED_BYTES=200*1024*1024;
    /** Inkwash: the front cover's and the back cover's place, for {@link #select}, {@link #index} and the rest; no page's. */
    static final int FRONT=-1, BACK=-2;
    /** What {@link #after} and {@link #before} give past the covers. */
    static final int NONE=Integer.MIN_VALUE;
    final int width,height;
    private final ArrayList<PackedPage> pages=new ArrayList<>();
    private final LinkedHashMap<Integer,ToneDocument> cache=new LinkedHashMap<>(4,.75f,true);
    private final java.util.HashMap<Integer,Long> packedRevisions=new java.util.HashMap<>();
    // Unsaved changes: book-level edits (pages, canvas choices, edited pages leaving the cache)
    // plus each decoded page's revision as of the last save or load.
    private long edits,savedEdits;
    /**
     * Inkwash: the day (epoch day) a page was last added or changed, as saved ({@code BookOptions}' {@code LAST}; -1: not
     * known, a file from before it), and the page edits (added, copied, deleted, changed and let go) since the last save.
     */
    private long lastDay=-1,pageEdits,savedPageEdits;
    /** Inkwash: today, as an epoch day (a check sets its own; -1 keeps no day). */
    java.util.function.LongSupplier today=()->java.time.LocalDate.now().toEpochDay();
    private final java.util.HashMap<Integer,Long> savedRevisions=new java.util.HashMap<>();
    private int index;
    private final ArrayList<Integer> canvasChoices=new ArrayList<>();
    /**
     * Each page's small picture (Inkwash's {@code PagePicture}, opaque here), or null: it moves with its page and is
     * dropped once the page changes. For a decoded page, {@link #pictureRevisions} holds the revision it shows.
     */
    private final ArrayList<byte[]> pictures=new ArrayList<>();
    private final java.util.HashMap<Integer,Long> pictureRevisions=new java.util.HashMap<>();
    /**
     * Inkwash: the page glued on the front cover as its picture, or -1 for none; it follows its page, and goes when its
     * page is deleted. (Before covers, the page a shelf showed for the book.)
     */
    private int cover=-1;
    /** Inkwash: the covers' packed bytes, front then back: null while nothing is drawn on one (it costs nothing). */
    private final PackedPage[] covers=new PackedPage[2];
    /** The covers' pictures, front then back (of their marks alone, {@code PagePicture#marksAlone}), as {@link #pictures}. */
    private final byte[][] coverPictures=new byte[2][];
    /** The notebook's name (its file's: its covers' seed and their label's words). */
    private String name="";
    /** Inkwash: its covers' design ({@code BookOptions}: kept only when it isn't the default). */
    private CoverDesign design=CoverDesign.DEFAULT;
    /** Inkwash: the notebook's other options ({@code BookOptions}: kept only what isn't the default): its template and tools. */
    private NotebookOptions options=NotebookOptions.DEFAULT;
    /**
     * Inkwash: tracing paper ({@link TracingPaper}): the Paper, the page pinned as the ghost (-1: none; it follows its page
     * as {@link #cover} does) and each page's step, kept beside {@link #pictures}.
     */
    private int paper=TracingPaper.PLAIN,ghost=-1,strength=TracingPaper.HALF;
    private final ArrayList<Integer> steps=new ArrayList<>();
    /** Inkwash: each page's own template key (null: none), kept beside {@link #steps}: what Random gave it, for good ({@link NotebookOptions#pageTemplate}). */
    private final ArrayList<String> templates=new ArrayList<>();
    /** Inkwash: what Random draws a new page's template with (a check may seed it). */
    java.util.Random dice=new java.util.Random();
    /** What each decoded cover's paper was made from ({@link #paperKey}), front then back. */
    private final String[] paperKeys=new String[2];
    int canvasChoice() { return isCover(index)?1:canvasChoices.get(index); }
    void setCanvasChoice(int choice) {
        if(choice<0 || choice>2)throw new IllegalArgumentException("Invalid canvas choice");
        // A cover keeps its size.
        if(isCover(index))return;
        if(canvasChoices.set(index,choice)!=choice)edits++;
    }

    DrawingBook(ToneDocument first) {
        width=first.width;height=first.height;pages.add(null);canvasChoices.add(0);pictures.add(null);steps.add(0);templates.add(null);cache.put(0,first);
        savedRevisions.put(0,first.revision());
    }
    DrawingBook(int width,int height,ArrayList<byte[]> pages,int index) throws IOException {
        this(width,height,held(pages),index,null);
    }
    private static ArrayList<PackedPage> held(java.util.List<byte[]> bytes) {
        ArrayList<PackedPage> held=new ArrayList<>();
        for(byte[] page:bytes)held.add(PackedPage.of(page));
        return held;
    }
    DrawingBook(int width,int height,java.util.List<PackedPage> pages,int index,ToneDocument decoded) throws IOException {
        ToneDocument.validateSize(width,height);this.width=width;this.height=height;
        if(pages.isEmpty()||pages.size()>MAX_PAGES||index<0||index>=pages.size())throw new IOException("Invalid page list");
        this.pages.addAll(pages); for(int i=0;i<pages.size();i++){canvasChoices.add(0);pictures.add(null);steps.add(0);templates.add(null);} this.index=index;cache.put(index,decoded==null?decode(pages.get(index)):decoded);
        packedRevisions.put(index,current().revision());savedRevisions.put(index,current().revision());
    }
    int count(){return pages.size();}
    /** The page drawn on: 0 to {@link #count} − 1, or a cover ({@link #FRONT}, {@link #BACK}). */
    int index(){return index;}
    /** The page drawn on, told the notebook's Lock Pages ({@link ToneDocument#locked}). */
    ToneDocument current(){ToneDocument page=cache.get(index);if(page!=null)page.timerLocks(options.lockPages);return page;}

    // Inkwash: the notebook's order, front cover, pages, back cover

    /** Whether {@code i} is a cover's place. */
    static boolean isCover(int i){return i==FRONT||i==BACK;}
    private static int side(int i){return i==FRONT?0:1;}
    private static int coverAt(int side){return side==0?FRONT:BACK;}
    /** Whether {@code i} is a page or a cover of this book. */
    boolean valid(int i){return isCover(i)||i>=0&&i<count();}
    /** What comes after {@code i}: the first page after the front cover, the back cover after the last page; {@link #NONE} after the back. */
    int after(int i){ if(i==FRONT)return 0; if(i==BACK)return NONE; return i+1<count()?i+1:BACK; }
    /** What comes before {@code i}: the front cover before the first page, the last page before the back cover; {@link #NONE} before the front. */
    int before(int i){ if(i==BACK)return count()-1; if(i==FRONT)return NONE; return i>0?i-1:FRONT; }
    /** Every place, in order: the front cover, the pages, the back cover. */
    private int[] places(){
        int[] all=new int[count()+2]; all[0]=FRONT; all[all.length-1]=BACK;
        for(int i=0;i<count();i++)all[i+1]=i;
        return all;
    }
    private PackedPage packedAt(int i){return isCover(i)?covers[side(i)]:pages.get(i);}
    private void setPackedAt(int i,PackedPage packed){if(isCover(i))covers[side(i)]=packed;else pages.set(i,packed);}
    private byte[] pictureAt(int i){return isCover(i)?coverPictures[side(i)]:pictures.get(i);}
    private void setPictureAt(int i,byte[] picture){if(isCover(i))coverPictures[side(i)]=picture;else pictures.set(i,picture);}
    /** Every page's packed bytes, with the covers': what the 200 MB limit counts. */
    private long packedBytes(){
        long size=0;
        for(PackedPage p:pages)if(p!=null)size+=p.length;
        for(PackedPage p:covers)if(p!=null)size+=p.length;
        return size;
    }

    /**
     * Inkwash: the notebook's {@code name} (its file's, without its folder: its covers' seed and their label's words). A
     * decoded cover's paper follows; whether the one drawn on changed (to show again).
     */
    boolean name(String name){
        name=name==null?"":name;
        if(name.equals(this.name))return false;
        this.name=name;
        return refreshCovers();
    }
    String name(){return name;}
    /** Inkwash: the covers' design. */
    CoverDesign design(){return design;}
    /**
     * Inkwash: the covers' design made {@code design}, a change to save; the cover drawn on gets its paper again at the
     * next {@link #refreshCovers} (its caller's: the new paper may take a while to make), another when turned to.
     */
    void setDesign(CoverDesign design){
        if(design==null||design==this.design)return;
        this.design=design; edits++;
    }
    /** Inkwash: the design a file or recovery held: not a change. */
    void restoreDesign(CoverDesign design){this.design=design==null?CoverDesign.DEFAULT:design;}
    /** Inkwash: the notebook's options. */
    NotebookOptions options(){return options;}
    /** Inkwash: the options made {@code options}, a change to save; every page decoded shows the new template's guides (its caller's to show again). */
    void setOptions(NotebookOptions options){
        if(options==null||options.equals(this.options))return;
        int was=this.options.paper;
        this.options=options; edits++; fillTemplates(); guideCached(); current();
        if(options.paper!=was)repaperBlank(was);
    }
    /**
     * Inkwash: Page Colour changed from {@code was}: every blank page on that paper (nothing on it, so nothing is lost)
     * takes the new one, the page drawn on among them; a page with anything on it keeps its
     * own, and so does every layer of its stack (a stack shows on one paper). A page put away is changed only when it is
     * the blank page's own bytes ({@link BlankPages#madeTone}), so no page is decoded.
     */
    private void repaperBlank(int was){
        PageStacks s=stacks();
        stack:
        for(int k=0;k<s.count();k++){
            for(int i=s.first(k);i<=s.last(k);i++){ ToneDocument page=cached(i); if(page!=null?!BlankPages.blank(page,was):BlankPages.madeTone(pages.get(i))!=was)continue stack; }
            for(int i=s.first(k);i<=s.last(k);i++){
                ToneDocument page=cached(i);
                if(page!=null){ page.setPaper(options.paper); continue; }
                try{ BlankPages.Made made=BlankPages.made(width,height,options.paper); pages.set(i,PackedPage.of(made.bytes)); pictures.set(i,made.picture); }
                catch(IOException|RuntimeException kept){ /* it stays on the old paper: still a blank page */ }
            }
        }
    }
    /** Inkwash: a new page, blank on the notebook's Page Colour. */
    private ToneDocument blankPage(){ ToneDocument page=new ToneDocument(width,height); page.placePaper(options.paper); return page; }
    /** Inkwash: the options a file or recovery held: not a change. */
    void restoreOptions(NotebookOptions options){this.options=options==null?NotebookOptions.DEFAULT:options; fillTemplates(); guideCached(); current();}
    /** Inkwash: each page's own template key a file or recovery held (null: none, or for another page count), not a change. */
    void restorePageTemplates(java.util.List<String> keys){
        if(keys!=null&&keys.size()==count())for(int i=0;i<count();i++)templates.set(i,keys.get(i));
        fillTemplates(); guideCached();
    }
    /** Inkwash: each page's own template key (null: none), for its file. */
    java.util.List<String> pageTemplates(){return new ArrayList<>(templates);}
    /**
     * Inkwash: page {@code i}'s own template made {@code t} (a hold on it in the strip), a change to save; the notebook's
     * own template (or Random) puts it back to the notebook's (under Random, a new pick).
     */
    void setPageTemplate(int i,NotebookTemplate t){
        if(isCover(i)||i<0||i>=count())return;
        // A stack's guides are its bottom page's.
        i=stacks().bottom(i);
        String key=t.random()||t.equals(options.template)?null:NotebookOptions.ownKey(t);
        if(java.util.Objects.equals(key,templates.get(i)))return;
        templates.set(i,key);edits++;fillTemplates();guideCached();
    }
    /** Inkwash: page {@code i}'s template (a cover's: Blank): its stack's bottom page's, under every layer of it. */
    NotebookTemplate template(int i){return isCover(i)||i<0||i>=count()?NotebookTemplate.BLANK:options.pageTemplate(templates.get(stacks().bottom(i)));}
    /** Inkwash: under Random, every page without a template of its own is given one now, and keeps it. */
    private void fillTemplates(){ for(int i=0;i<count();i++)if(templates.get(i)==null)templates.set(i,newTemplate(i)); }
    /** Inkwash: the own template of a page at {@code at} (in {@link #templates}): Random's pick, never its neighbours' while the set has another, or none. */
    private String newTemplate(int at){
        if(!options.template.random())return null;
        return options.template.pick(dice,neighbour(at-1),neighbour(at+1)).key;
    }
    private NotebookTemplate neighbour(int i){return i<0||i>=templates.size()||templates.get(i)==null?null:options.pageTemplate(templates.get(i));}
    /** Inkwash: tracing paper as it stands. */
    TracingPaper tracing(){
        int[] s=new int[steps.size()];
        for(int i=0;i<s.length;i++)s[i]=steps.get(i)&~(LAST|OWN);
        return new TracingPaper(paper,ghost,s,strength);
    }
    /**
     * Inkwash: a page's own rules ({@link NotebookRules#own}: No Erasing and Timed Pages, each the notebook's, Off or On for
     * the page alone; Page Settings, the owner, 2026-10-23), kept in its bits beside its steps so they go wherever the page
     * goes (moved, taken and put back); never in {@link #tracing}. A stack's are its bottom page's, as its template is.
     */
    private static final int RULE_SHIFT=18,RULES=15<<RULE_SHIFT;
    /**
     * Inkwash: a page's own time (Page Settings' slider, the owner, 2026-10-23: "it should be a slider"), beside its rules:
     * the stop of {@link NotebookOptions#SECONDS} (0: none, the notebook's or 5 min), used while its Timed Pages is On.
     * {@link #OWN} is everything that is the page's own.
     */
    private static final int TIME_SHIFT=22,TIMES=127<<TIME_SHIFT,OWN=RULES|TIMES;
    /** Inkwash: page {@code i}'s own rules, its stack's (a cover: none). */
    int pageRules(int i){return isCover(i)||i<0||i>=count()?0:steps.get(stacks().bottom(i))>>>RULE_SHIFT&15;}
    /** Inkwash: page {@code i}'s stack's own time, s (0: none). */
    int pageSeconds(int i){return isCover(i)||i<0||i>=count()?0:secondsOf(steps.get(stacks().bottom(i)));}
    private static int secondsOf(int bits){int stop=bits>>>TIME_SHIFT&127;return stop<NotebookOptions.SECONDS.length?NotebookOptions.SECONDS[stop]:0;}
    private static int stopOf(int seconds){return seconds<=0?0:Math.max(1,NotebookOptions.nearest(NotebookOptions.SECONDS,seconds));}
    /** Inkwash: page {@code i}'s stack's own time made {@code seconds} (the nearest stop; 0: none), a change to save. */
    void setPageSeconds(int i,int seconds){
        if(isCover(i)||i<0||i>=count())return;
        i=stacks().bottom(i);
        int was=steps.get(i),now=was&~TIMES|stopOf(seconds)<<TIME_SHIFT;
        if(now!=was){steps.set(i,now);edits++;}
    }
    /** Inkwash: every page's own time, s, for its file. */
    int[] pageSeconds(){int[] s=new int[count()];for(int i=0;i<s.length;i++)s[i]=secondsOf(steps.get(i));return s;}
    /** Inkwash: the pages' own times a file or recovery held (null, or for another page count: none), not a change. */
    void restorePageSeconds(int[] seconds){
        for(int i=0;i<count();i++){int s=seconds!=null&&seconds.length==count()?seconds[i]:0;steps.set(i,steps.get(i)&~TIMES|stopOf(s)<<TIME_SHIFT);}
    }
    /** Inkwash: page {@code i}'s stack's own rules made {@code rules}, a change to save. */
    void setPageRules(int i,int rules){
        if(isCover(i)||i<0||i>=count()||!NotebookRules.validOwn(rules))return;
        i=stacks().bottom(i);
        int was=steps.get(i),now=was&~RULES|rules<<RULE_SHIFT;
        if(now!=was){steps.set(i,now);edits++;}
    }
    /** Inkwash: the rules in force on page {@code i}: the notebook's, with the page's own over them. */
    NotebookOptions rules(int i){return NotebookRules.forPage(options,pageRules(i),pageSeconds(i));}
    /** Inkwash: every page's own rules, for its file. */
    int[] pageRules(){int[] r=new int[count()];for(int i=0;i<r.length;i++)r[i]=steps.get(i)>>>RULE_SHIFT&15;return r;}
    /** Inkwash: the pages' own rules a file or recovery held (null, or for another page count: none), not a change. */
    void restorePageRules(int[] rules){
        for(int i=0;i<count();i++){int r=rules!=null&&rules.length==count()&&NotebookRules.validOwn(rules[i])?rules[i]:0;steps.set(i,steps.get(i)&~RULES|r<<RULE_SHIFT);}
    }
    /** Inkwash: whether page {@code i}'s stack has a template of its own (a hold's, not the notebook's or Random's pick). */
    boolean ownTemplate(int i){
        if(isCover(i)||i<0||i>=count())return false;
        String key=templates.get(stacks().bottom(i));
        return key!=null&&key.startsWith(NotebookOptions.OWN);
    }
    /** Inkwash: page {@code i}'s stack's paper tone (its bottom page's): read from the page itself, decoded for it when it isn't. */
    int paperTone(int i) throws IOException {
        if(isCover(i)||i<0||i>=count())return NotebookOptions.WHITE;
        i=stacks().bottom(i);
        ToneDocument page=cached(i);
        if(page!=null)return page.paperTone();
        PackedPage packed=pages.get(i);
        if(packed==null)return options.paper;
        int made=BlankPages.madeTone(packed);
        return made>=0?made:decode(packed).paperTone();
    }
    /**
     * Inkwash: a page's bit beside its step bits marking it the layer of its stack drawn on last (the owner, 2026-10-21:
     * turning to a stack lands there): memory only, never in {@link #tracing}, a file or the recovery.
     */
    private static final int LAST=1<<17;
    /** Inkwash: the page a turn to stack place {@code place} draws on: the layer of it drawn on last this session, else its top; a cover itself. */
    int pageOf(int place){
        PageStacks s=stacks();
        if(isCover(place))return place;
        for(int p=s.first(place);p<=s.last(place);p++)if((steps.get(p)&LAST)!=0)return p;
        return s.last(place);
    }
    /** Page {@code i} (not a cover) marked its stack's layer drawn on last. */
    private void drawnLast(int i){
        if(isCover(i))return;
        PageStacks s=stacks();
        int st=s.of(i);
        for(int p=s.first(st);p<=s.last(st);p++)steps.set(p,p==i?steps.get(p)|LAST:steps.get(p)&~LAST);
    }
    /** Inkwash: the stacks the pages make ({@link PageStacks}): a layer is a page stacked on the one below. */
    PageStacks stacks(){
        boolean[] links=new boolean[steps.size()];
        for(int i=0;i<links.length;i++)links[i]=linked(i);
        return new PageStacks(links);
    }
    /** Inkwash: whether page {@code i} is stacked on the page below, and so marked or not. */
    boolean linked(int i){return i>0&&i<steps.size()&&(steps.get(i)&TracingPaper.LINKED)!=0;}
    private void link(int i,boolean on){if(i>=0&&i<steps.size())steps.set(i,on&&i>0?steps.get(i)|TracingPaper.LINKED:steps.get(i)&~TracingPaper.LINKED);}
    /** Inkwash: tracing paper made {@code tracing} (the Paper, the ghost, a step), a book edit like the pages' order; free under the lock and the rules. */
    void setTracing(TracingPaper tracing){
        if(tracing==null||tracing.count()!=count()||tracing.equals(tracing()))return;
        restoreTracing(tracing); edits++;
    }
    /** Inkwash: tracing paper a file or recovery held, not a change; one for another page count is the default. */
    void restoreTracing(TracingPaper tracing){
        if(tracing==null||tracing.count()!=count())tracing=TracingPaper.plain(count());
        paper=tracing.paper; ghost=tracing.ghost; strength=tracing.strength;
        for(int i=0;i<count();i++)steps.set(i,tracing.stepBits(i)|steps.get(i)&(LAST|OWN));
    }
    /**
     * Inkwash: what the page drawn on's ghosts are made from ({@link TracingPaper#ghosts}): each ghost page, its strength
     * and what it holds now; null for none. Pages other than the one drawn on don't change while it is, so this changes
     * only with a page turn, the Paper, a step, the ghost or the order.
     */
    String ghostKey(){
        int[][] ghosts=tracing().ghostLayers(index);
        if(ghosts.length==0)return null;
        StringBuilder key=new StringBuilder().append(index);
        for(int[] g:ghosts)key.append(' ').append(g[0]).append('@').append(g[1]).append('x').append(g[2]).append('g').append(g[3]).append('/').append(layerBits(g[0])).append(contentKey(g[0]));
        return key.toString();
    }
    /** Inkwash: page {@code page}'s layer steps ({@link TracingPaper#layerBits}) when it is the page drawn on, else 0 (a cover: none). */
    int layerSteps(ToneDocument page){return page==null||page!=current()||isCover(index)?0:layerBits(index);}
    /** Page {@code i}'s old in-page layers' steps ({@link TracingPaper#layerBits}). */
    private int layerBits(int i){return (steps.get(i)&TracingPaper.LAYER_BITS)>>>8;}
    /**
     * Inkwash: the page drawn on's ghosts as {@link GhostPlane} reads them, off this thread: a decoded page's layers (a
     * snapshot), any other its packed bytes; none enters the cache.
     */
    java.util.List<GhostPlane.Source> ghostSources(){
        java.util.List<GhostPlane.Source> out=new ArrayList<>();
        for(int[] g:tracing().ghostLayers(index)){
            ToneDocument page=cached(g[0]);
            PackedPage packed=pages.get(g[0]);
            // A layer above the page drawn on, in its stack, lies over it (Stage 3c); another stack shows flattened (its group).
            boolean above=g[3]<0&&g[0]>index;
            if(page!=null||packed!=null)out.add(new GhostPlane.Source(page==null?null:page.layerSnapshot(),page==null?packed:null,g[1],layerBits(g[0]),g[3],g[2],above));
        }
        return out;
    }
    /** What page {@code i} holds now: a decoded page's identity and revision, else its packed bytes' length and CRC. */
    private String contentKey(int i){
        ToneDocument page=cached(i);
        PackedPage p=packedAt(i);
        if(page!=null)return ":d"+System.identityHashCode(page)+'@'+page.revision();
        return p!=null?":p"+p.length+'/'+p.crc:"";
    }
    /** Page {@code i} of this book (not a cover), with its template's guides under it as the screen shows them ({@link NotebookOptions#guidesShown}; shared by every page of the template). */
    private ToneDocument guided(int i,ToneDocument page){ page.placeGuides(template(i).shown(width,height,options.guidesShown)); return page; }
    private void guideCached(){ for(java.util.Map.Entry<Integer,ToneDocument> page:cache.entrySet())if(!isCover(page.getKey()))guided(page.getKey(),page.getValue()); }
    /**
     * Makes the paper of the cover drawn on again if what it is made from changed (the name, the cover picture); whether
     * it did. A cover not drawn on now gets its paper again when turned to ({@link #select}), so turning pages never
     * makes one.
     */
    boolean refreshCovers(){
        if(!isCover(index)||paperKey(index).equals(paperKeys[side(index)]))return false;
        placePaper(index,current());
        return true;
    }
    /** What cover {@code i}'s paper is made from: the design, the name, and the front's picture page as it is now. */
    private String paperKey(int i){
        StringBuilder key=new StringBuilder(design.key).append('\n').append(name).append('\n');
        if(i==BACK)return key.toString();
        key.append(cover);
        if(cover>=0)key.append(contentKey(cover));
        return key.toString();
    }
    /** Lays cover {@code i}'s paper on {@code page}, made now from what {@link #paperKey} names. */
    private void placePaper(int i,ToneDocument page){
        NotebookCovers.Picture picture=null;
        if(i==FRONT&&cover>=0){
            ToneDocument pictured=cached(cover);
            try{ if(pictured==null)pictured=decode(pages.get(cover)); picture=NotebookCovers.of(pictured.layerSnapshot()); }
            catch(IOException|RuntimeException damaged){ picture=null; }
        }
        NotebookCovers.Picture glued=NotebookCovers.once(picture); CoverDesign made=design; String named=name;
        page.placePaper(NotebookCovers.paper(i==FRONT,made,named,width,height,glued),()->NotebookCovers.paper(i==FRONT,made,named,width,height,glued,true));
        paperKeys[side(i)]=paperKey(i);
    }
    /** Whether something is drawn on cover {@code i} (decoded or not). */
    boolean coverDrawn(int i){
        ToneDocument page=cached(i);
        return page!=null?!page.unmarked():covers[side(i)]!=null;
    }

    void select(int target) throws IOException {
        if(!valid(target))throw new IllegalArgumentException("Invalid page");
        if(target==index)return;
        retainCurrent();
        drawnLast(index);
        ToneDocument next=cache.get(target);
        if(next==null) next=decodePage(target);
        else if(isCover(target)&&!paperKey(target).equals(paperKeys[side(target)]))placePaper(target,next);
        index=target;cache.put(index,next);evict();
    }
    void addPage() throws IOException {
        if(count()>=MAX_PAGES)throw new IOException(FULL_PAGES);
        retainCurrent();
        index=pages.size();pages.add(null);canvasChoices.add(0);pictures.add(null);steps.add(0);templates.add(newTemplate(templates.size()));
        cache.put(index,guided(index,blankPage()));edits++;pageEdits++;evict();
    }
    /**
     * Inkwash: the page bar's + tile (the owner, 2026-10-24: "the + should be where the auto new blank page used to be"):
     * a new page at the notebook's end, on its Page Colour with its template, drawn on.
     */
    void insertPage() throws IOException { addPage(); }
    /**
     * Inkwash: page {@code i}, not the page drawn on, taken out not as a change (an old notebook's blank page at its end,
     * {@code BlankPages#opened}): a save leaves it out with what else changed.
     */
    void dropUnchanged(int i) throws IOException {
        long changes=edits,pageChanges=pageEdits;
        deletePage(i); edits=changes; pageEdits=pageChanges;
    }
    /**
     * Inkwash: adds {@code page}, packed, after the last with its {@code picture}, the current page staying current, and
     * not as a change (the benches' and checks' quick pages): a save writes it with what else changed. False, changing
     * nothing, when the book is full.
     */
    boolean appendUnchanged(PackedPage page,byte[] picture) {
        if(count()>=MAX_PAGES||packedBytes()+page.length>MAX_PACKED_BYTES)return false;
        pages.add(page);canvasChoices.add(0);pictures.add(picture);steps.add(0);templates.add(newTemplate(templates.size()));
        return true;
    }
    /** Inserts a copy of the current page right after it, stacked on it (a layer), without its undo history, and selects the copy. */
    void duplicatePage() throws IOException {
        if(isCover(index))throw new IOException(COVER);
        if(count()>=MAX_PAGES)throw new IOException(FULL_PAGES);
        retainCurrent();
        PackedPage packed=currentPacked();
        if(packedBytes()+packed.length>MAX_PACKED_BYTES)throw new IOException(FULL);
        ToneDocument copy=guided(index,decode(packed));byte[] picture=picture(index);
        // A copy of a timed page starts its own clock (and Timed Sections), unlocked: the packed bytes hold the original's, so the copy is packed again.
        boolean timed=copy.clock().kept()||copy.sections().started();if(timed){copy.setClock(PageClock.NONE);copy.setSections(TimedSections.NONE);}
        int target=index+1;
        shiftPages(target,1);
        // The copy is a layer stacked on its original (Duplicate, the side column's layers): a stack stays one run.
        pages.add(target,packed);canvasChoices.add(target,canvasChoice());pictures.add(target,picture);steps.add(target,steps.get(index)|TracingPaper.LINKED);templates.add(target,templates.get(index));
        if(cover>=target)cover++;
        if(ghost>=target)ghost++;
        index=target;cache.put(index,copy);if(!timed)packedRevisions.put(index,copy.revision());
        if(picture!=null)pictureRevisions.put(index,copy.revision());
        edits++;pageEdits++;evict();
    }
    /** Removes the current page and selects the page after it, or the one before when it was last. */
    void deletePage() throws IOException {
        if(isCover(index))throw new IOException(COVER);
        if(count()<=1)throw new IOException(LAST_PAGE);
        // A layer goes: the layer below it is drawn on (the one above, for the bottom); a page alone: the page after, else before.
        int removed=index,next=linked(removed)?removed-1:removed+1<count()?removed+1:removed-1;
        // Decode the page to show before changing anything, so a damaged page leaves the book whole.
        ToneDocument shown=cache.get(next);
        if(shown==null) shown=decodePage(next);
        cache.remove(removed);packedRevisions.remove(removed);savedRevisions.remove(removed);pictureRevisions.remove(removed);
        unstack(removed);
        pages.remove(removed);canvasChoices.remove(removed);pictures.remove(removed);steps.remove(removed);templates.remove(removed);
        cover=cover==removed?-1:cover>removed?cover-1:cover;
        ghost=ghost==removed?-1:ghost>removed?ghost-1:ghost;
        shiftPages(removed+1,-1);
        index=Math.min(next,removed);cache.put(index,shown);edits++;pageEdits++;evict();guideCached();
    }
    /** Page {@code i} is about to go: a stack's bottom going, the layer on it becomes the bottom (its guides with it). */
    private void unstack(int i){
        if(linked(i)||!linked(i+1))return;
        link(i+1,false);
        // The stack's guides, own rules, ghost pin and cover picture go with it to the layer on the bottom.
        templates.set(i+1,templates.get(i));
        steps.set(i+1,steps.get(i+1)&~OWN|steps.get(i)&OWN);
        if(cover==i)cover=i+1;
        if(ghost==i)ghost=i+1;
    }
    /**
     * Removes page {@code page}, not decoding any page when it isn't the current one (the notebook view deletes the page
     * it shows, which needn't be the one being drawn on); the current page stays current.
     */
    void deletePage(int page) throws IOException {
        if(page==index){deletePage();return;}
        if(page<0||page>=count())throw new IllegalArgumentException("Invalid page");
        if(count()<=1)throw new IOException(LAST_PAGE);
        cache.remove(page);packedRevisions.remove(page);savedRevisions.remove(page);pictureRevisions.remove(page);
        unstack(page);
        pages.remove(page);canvasChoices.remove(page);pictures.remove(page);steps.remove(page);templates.remove(page);
        shiftPages(page+1,-1);guideCached();
        if(index>page)index--;
        cover=cover==page?-1:cover>page?cover-1:cover;
        ghost=ghost==page?-1:ghost>page?ghost-1:ghost;
        edits++;pageEdits++;
    }
    /**
     * Inkwash: page {@code from} moved to place {@code to} (docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md,
     * "Reorder"), the pages between shifting one place: its bytes, picture and canvas choice go with it, and so do the
     * decoded pages, their revisions, the cover picture's page and the page drawn on. No page's bytes change. One book
     * edit, not undoable (moving it back reverses it).
     */
    void move(int from,int to) {
        if(from<0||from>=count()||to<0||to>=count())throw new IllegalArgumentException("Invalid page");
        if(from==to)return;
        pages.add(to,pages.remove(from));canvasChoices.add(to,canvasChoices.remove(from));pictures.add(to,pictures.remove(from));steps.add(to,steps.remove(from));templates.add(to,templates.remove(from));
        LinkedHashMap<Integer,ToneDocument> moved=new LinkedHashMap<>(4,.75f,true);
        for(java.util.Map.Entry<Integer,ToneDocument> page:cache.entrySet())moved.put(moved(page.getKey(),from,to),page.getValue());
        cache.clear();cache.putAll(moved);
        for(java.util.HashMap<Integer,Long> map:java.util.Arrays.asList(packedRevisions,savedRevisions,pictureRevisions)){
            java.util.HashMap<Integer,Long> keyed=new java.util.HashMap<>();
            for(java.util.Map.Entry<Integer,Long> page:map.entrySet())keyed.put(moved(page.getKey(),from,to),page.getValue());
            map.clear();map.putAll(keyed);
        }
        if(cover>=0)cover=moved(cover,from,to);
        if(ghost>=0)ghost=moved(ghost,from,to);
        index=moved(index,from,to);
        edits++;
    }
    /**
     * Inkwash: a new layer (the spec's "Stage 3c"): a blank page stacked onto the page drawn on, {@code above} it or below
     * it, drawn on. False, changing nothing, on a cover, with the stack full ({@link PageStacks#MOST}) or the book full.
     */
    boolean addLayer(boolean above) throws IOException {
        if(isCover(index)||count()>=MAX_PAGES)return false;
        PageStacks s=stacks();
        if(s.size(s.of(index))>=PageStacks.MOST)return false;
        int at=above?index+1:index;
        boolean bottom=!linked(index);
        // A layer is on its stack's paper, not the notebook's Page Colour: the stack shows as one page.
        int paper=current().paperTone();
        addPage(); current().placePaper(paper); move(count()-1,at);
        // Above: stacked on the page drawn on. Below: in its place in the stack, the page drawn on stacked on it.
        if(above)link(at,true); else { link(at,!bottom); link(at+1,true); if(bottom){templates.set(at,templates.get(at+1));steps.set(at,steps.get(at)&~OWN|steps.get(at+1)&OWN);} }
        guideCached();
        return true;
    }
    /**
     * Inkwash: stack {@code from} (its layers together) moved to stack place {@code to}, the stacks between shifting one
     * place, as {@link #move} moves a page: one book edit a layer, nothing undoable.
     */
    void moveStack(int from,int to){
        PageStacks s=stacks();
        if(from<0||to<0||from>=s.count()||to>=s.count()||from==to)return;
        int first=s.first(from),n=s.size(from);
        if(to>from){int target=s.last(to);for(int k=0;k<n;k++)move(first,target);}
        else{int target=s.first(to);for(int k=0;k<n;k++)move(first+k,target+k);}
    }
    /**
     * Inkwash: page {@code i} as it is now, to put back after it goes ({@link #restore}): its bytes (packed now if it
     * changed), picture, canvas choice, bits, template, and whether it was the cover picture, the ghost, and the layer on it.
     */
    Taken take(int i) throws IOException {
        if(isCover(i)||i<0||i>=count())throw new IllegalArgumentException("Invalid page");
        ToneDocument page=cached(i);
        PackedPage packed=page!=null&&!retained(i)?PackedPage.of(encode(page,DocumentCodec.QUICK),true):pages.get(i);
        return new Taken(i,packed,picture(i),canvasChoices.get(i),steps.get(i),templates.get(i),cover==i,ghost==i,linked(i+1));
    }
    /** Inkwash: a page {@link #take} took, back in its place (not drawn on: its caller selects it); one book edit. */
    void restore(Taken t) throws IOException {
        if(count()>=MAX_PAGES||t.at>count())throw new IOException(FULL_PAGES);
        shiftPages(t.at,1);
        pages.add(t.at,t.packed);canvasChoices.add(t.at,t.choice);pictures.add(t.at,t.picture);steps.add(t.at,t.bits);templates.add(t.at,t.template);
        if(cover>=t.at)cover++;
        if(ghost>=t.at)ghost++;
        if(index>=t.at)index++;
        if(t.cover)cover=t.at;
        if(t.ghost)ghost=t.at;
        link(t.at+1,t.above);
        edits++;pageEdits++;guideCached();
    }
    /** Inkwash: a page as {@link #take} took it. */
    static final class Taken {
        final int at,choice,bits; final PackedPage packed; final byte[] picture; final String template; final boolean cover,ghost,above;
        Taken(int at,PackedPage packed,byte[] picture,int choice,int bits,String template,boolean cover,boolean ghost,boolean above){
            this.at=at;this.packed=packed;this.picture=picture;this.choice=choice;this.bits=bits;this.template=template;this.cover=cover;this.ghost=ghost;this.above=above;
        }
    }
    /** Inkwash: the book's edits so far (pages, their order, tracing paper, options): what an undo of a layer change checks nothing else changed since. */
    long changes(){return edits;}
    /** Where page {@code i} is once page {@code from} moved to {@code to}; a cover's place stays. */
    static int moved(int i,int from,int to){
        if(isCover(i))return i;
        if(i==from)return to;
        if(from<to&&i>from&&i<=to)return i-1;
        if(from>to&&i>=to&&i<from)return i+1;
        return i;
    }
    /** Moves the cached documents and revisions of pages from {@code first} on by {@code by} (the covers, below 0, stay). */
    private void shiftPages(int first,int by) {
        LinkedHashMap<Integer,ToneDocument> shifted=new LinkedHashMap<>(4,.75f,true);
        for(java.util.Map.Entry<Integer,ToneDocument> page:cache.entrySet())shifted.put(shift(page.getKey(),first,by),page.getValue());
        cache.clear();cache.putAll(shifted);
        shiftKeys(packedRevisions,first,by);shiftKeys(savedRevisions,first,by);shiftKeys(pictureRevisions,first,by);
    }
    private static int shift(int page,int first,int by){return page>=first?page+by:page;}
    private static void shiftKeys(java.util.HashMap<Integer,Long> map,int first,int by) {
        java.util.HashMap<Integer,Long> shifted=new java.util.HashMap<>();
        for(java.util.Map.Entry<Integer,Long> page:map.entrySet())shifted.put(shift(page.getKey(),first,by),page.getValue());
        map.clear();map.putAll(shifted);
    }
    private void retainCurrent() throws IOException {
        // Inkwash: the page stays open for a quick turn back, without its undo (up to 32 MB a page).
        current().dropHistory();
        // Browsing does not change a page. Keep its already-compressed bytes,
        // including after decoding an evicted page, instead of deflating it again.
        if(retained(index))return;
        // Inkwash: a cover with nothing drawn keeps no bytes at all.
        if(isCover(index)&&current().unmarked()){covers[side(index)]=null;packedRevisions.put(index,current().revision());return;}
        // Inkwash: packed quickly, on the drawing thread; the book's file gets it packed as before (Snapshot.filed).
        PackedPage packed=PackedPage.of(encode(current(),DocumentCodec.QUICK),true);
        PackedPage was=packedAt(index);
        if(packedBytes()-(was==null?0:was.length)+packed.length>MAX_PACKED_BYTES)throw new IOException(FULL);
        setPackedAt(index,packed);packedRevisions.put(index,current().revision());
    }
    /** What a full book says (Inkwash's words: docs/TEXT-STYLE.md, "notebook"). */
    static final String FULL_PAGES="This notebook has "+MAX_PAGES+" pages", PAGES_FULL="Notebook full, "+MAX_PAGES+" pages", FULL="This notebook is full", LAST_PAGE="A notebook keeps one page at least",
            COVER="A cover stays";
    /** Whether the current page's packed bytes are what it holds now (a cover's none, when nothing is drawn on it). */
    private boolean retained(int i) {
        Long revision=packedRevisions.get(i);
        ToneDocument page=cached(i);
        return revision!=null&&page!=null&&revision==page.revision();
    }
    private PackedPage currentPacked() { return retained(index)?packedAt(index):null; }
    private void evict(){
        long pixels=0; for(ToneDocument page:cache.values()) pixels+=(long)page.width*page.height*page.layerCount()*2;
        while(cache.size()>1&&(cache.size()>2||pixels>32*1024*1024)) {
            int removedIndex=cache.keySet().iterator().next();
            ToneDocument removed=cache.remove(removedIndex);packedRevisions.remove(removedIndex);
            // A picture of what the page was before its last changes goes; one of the page as it is stays with its bytes.
            if(!Long.valueOf(removed.revision()).equals(pictureRevisions.remove(removedIndex)))setPictureAt(removedIndex,null);
            if(!Long.valueOf(removed.revision()).equals(savedRevisions.remove(removedIndex))){edits++;if(!isCover(removedIndex))pageEdits++;}
            if(isCover(removedIndex))paperKeys[side(removedIndex)]=null;
            pixels-=(long)removed.width*removed.height*removed.layerCount()*2;
        }
    }
    /** Page {@code i}, not decoded yet, decoded: as it was saved or loaded, its picture still its own; a cover on its paper. */
    private ToneDocument decodePage(int i) throws IOException {
        PackedPage packed=packedAt(i);
        ToneDocument page=packed==null?(isCover(i)?new ToneDocument(width,height):blankPage()):decode(packed);
        if(isCover(i))placePaper(i,page); else guided(i,page);
        packedRevisions.put(i,page.revision());savedRevisions.put(i,page.revision());
        if(pictureAt(i)!=null)pictureRevisions.put(i,page.revision());
        return page;
    }
    /** Page {@code i} if it is decoded, without counting as a use (the cache keeps the most recently used). */
    private ToneDocument cached(int i) {
        for(java.util.Map.Entry<Integer,ToneDocument> page:cache.entrySet())if(page.getKey()==i)return page.getValue();
        return null;
    }
    /** Whether page {@code i}'s packed bytes are what it holds now (a cover's: none while nothing is drawn on it). */
    private boolean packedCurrent(int i) {
        ToneDocument page=cached(i);
        if(page==null)return packedAt(i)!=null;
        return retained(i)&&packedAt(i)!=null;
    }

    // Pictures and the cover (Inkwash's notebooks: docs/archive/superpowers/specs/2026-10-18-notebooks-design.md)

    /** Page {@code i} if it is decoded (being drawn on, or just turned from), else null. */
    ToneDocument cachedPage(int i) { return cached(i); }
    /** Page {@code i}'s packed bytes if they are what it holds now, else null (it changed since it was packed; a cover with nothing drawn). */
    PackedPage packed(int i) { return packedCurrent(i) ? packedAt(i) : null; }
    /**
     * Takes {@code packed}, the current page packed as {@code key} named it (a save packs it anyway), so leaving the page
     * needn't pack it again; nothing if the page changed since.
     */
    void offerPacked(PictureKey key,PackedPage packed) { if(key.page==current())offerPacked(index,key,packed); }
    /** Page {@code i} takes {@code packed} if it still holds what {@code key} names and isn't packed yet, or only quickly. */
    private void offerPacked(int i,PictureKey key,PackedPage packed) {
        if(key.page==null||cached(i)!=key.page||key.page.revision()!=key.revision)return;
        // A page packed quickly gives way to the same page as its file holds it.
        PackedPage now=packed(i);
        if(now!=null&&(!now.quick||packed.quick))return;
        setPackedAt(i,packed);packedRevisions.put(i,key.revision);
    }
    /**
     * Inkwash: {@code saved}, taken from this book, reached its file: each page still holding the {@link PackedPage#quick
     * quick} bytes it was saved from reads the file's from now on (their memory freed), and so does the page that was
     * being drawn on if it is still as saved, current or not. Pages changed since keep what they hold. The covers too.
     */
    void savedPages(Snapshot saved) {
        java.util.List<PackedPage> written=saved.written;
        if(written==null)return;
        java.util.IdentityHashMap<PackedPage,PackedPage> filed=new java.util.IdentityHashMap<>();
        // The active page's slot may hold bytes of the page before its last changes: what was written for it is newer.
        for(int i=0;i<saved.pages.size();i++){PackedPage was=saved.pages.get(i);if(i!=saved.index&&was!=null&&was.quick)filed.put(was,written.get(i));}
        for(int s=0;s<2;s++){PackedPage was=saved.covers[s];if(coverAt(s)!=saved.index&&was!=null&&was.quick&&saved.writtenCovers[s]!=null)filed.put(was,saved.writtenCovers[s]);}
        for(int i:places()){PackedPage now=i==index?null:filed.get(packedAt(i));if(now!=null)setPackedAt(i,now);}
        if(saved.activeKey.page==null)return;
        PackedPage active=saved.writtenAt(saved.index);
        for(int i:places())if(cached(i)==saved.activeKey.page){
            if(active!=null)offerPacked(i,saved.activeKey,active);
            // A cover saved with nothing drawn on it keeps no bytes, as saved.
            else if(isCover(i)&&saved.activeKey.page.revision()==saved.activeKey.revision){covers[side(i)]=null;packedRevisions.put(i,saved.activeKey.revision);}
        }
    }
    /** Page {@code i}'s picture if it shows the page as it is now, else null. */
    byte[] picture(int i) {
        byte[] picture=pictureAt(i);
        if(picture==null)return null;
        ToneDocument page=cached(i);
        return page==null||Long.valueOf(page.revision()).equals(pictureRevisions.get(i))?picture:null;
    }
    /** What page {@code i} holds now, for a picture made of it later ({@link #offerPicture}). */
    PictureKey pictureKey(int i) {
        ToneDocument page=cached(i);
        return page!=null?new PictureKey(page,page.revision(),null):new PictureKey(null,0,packedAt(i));
    }
    /**
     * Takes {@code picture}, made of what {@code key} named, for every page that still holds just that (a duplicated
     * page shares its original's bytes); a page changed since is left as it is. Whether any page took it.
     */
    boolean offerPicture(PictureKey key,byte[] picture) {
        boolean taken=false;
        for(int i:places()) {
            ToneDocument page=cached(i);
            boolean same=key.page!=null?page==key.page&&page.revision()==key.revision
                    :key.packed!=null&&packedAt(i)==key.packed&&packedCurrent(i);
            if(!same)continue;
            setPictureAt(i,picture);taken=true;
            if(page!=null)pictureRevisions.put(i,page.revision());
        }
        return taken;
    }
    /** The pictures and cover picture a file held ({@code BookCodec}); a picture for each page, null where there is none; -1: no cover picture. */
    void restorePictures(byte[][] saved,int cover) {
        if(saved.length!=count())throw new IllegalArgumentException("Invalid pictures");
        for(int i=0;i<saved.length;i++) {
            pictures.set(i,saved[i]);
            ToneDocument page=cached(i);
            if(page!=null&&saved[i]!=null)pictureRevisions.put(i,page.revision());
        }
        this.cover=cover>=0&&cover<count()?cover:-1;
    }
    /** Inkwash: the covers a file held: their packed bytes (null: nothing drawn), front then back, and their pictures. */
    void restoreCovers(PackedPage[] packed,byte[][] saved) {
        for(int s=0;s<2;s++){ covers[s]=packed[s]; coverPictures[s]=packed[s]==null?null:saved[s]; }
    }
    /** The page glued on the front cover as its picture, or -1. */
    int cover(){return cover;}
    /** Makes page {@code page} the cover picture (-1: none); a change to save. */
    void setCover(int page) {
        if(page!=-1&&(page<0||page>=count()))throw new IllegalArgumentException("Invalid page");
        if(cover!=page){cover=page;edits++;}
    }
    /** A page's contents as a picture was made of them: a decoded page at a revision, or packed bytes. */
    static final class PictureKey {
        final ToneDocument page;final long revision;final PackedPage packed;
        PictureKey(ToneDocument page,long revision,PackedPage packed){this.page=page;this.revision=revision;this.packed=packed;}
    }

    private ToneDocument decode(PackedPage packed) throws IOException {
        return DocumentCodec.read(new ByteArrayInputStream(packed.bytes()));
    }
    static byte[] encode(ToneDocument document) throws IOException { return encode(document,java.util.zip.Deflater.DEFAULT_COMPRESSION); }
    /** {@code document} packed at a deflate {@code level}; its snapshot released after, so its next write copies no layer for it. */
    static byte[] encode(ToneDocument document,int level) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        ToneDocument.Snapshot snapshot=document.layerSnapshot();
        try { DocumentCodec.write(out,snapshot,level); } finally { snapshot.release(); }
        return out.toByteArray();
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
        savedPageEdits=saved.pageEdits; lastDay=Math.max(lastDay,saved.lastDay);
    }
    /** Inkwash: whether a page was added, deleted or changed since the last save (the covers aren't pages: theirs don't count). */
    boolean pagesChanged() {
        if(pageEdits!=savedPageEdits)return true;
        for(java.util.Map.Entry<Integer,ToneDocument> page:cache.entrySet())
            if(!isCover(page.getKey())&&!Long.valueOf(page.getValue().revision()).equals(savedRevisions.get(page.getKey())))return true;
        return false;
    }
    /** Inkwash: the day a page was last added to or changed: today while one is unsaved, else as saved (null: not known). */
    java.time.LocalDate lastAdded(){long day=pagesChanged()?today.getAsLong():lastDay;return day<0?null:java.time.LocalDate.ofEpochDay(day);}
    /** Inkwash: the day a file or recovery kept (-1: none), not a change. */
    void restoreLastDay(long day){lastDay=day;}
    /** For a recovered book whose changes were never saved. */
    void markUnsaved() { edits++; }
    Snapshot snapshot(){return new Snapshot(this);}
    static final class Snapshot {
        /** {@code index}: the page drawn on, or a cover ({@link #FRONT}, {@link #BACK}). */
        final int width,height,index;
        final ToneDocument.Snapshot activePage;
        final PackedPage packedActivePage;
        final ArrayList<PackedPage> pages;
        final ArrayList<Integer> canvasChoices;
        final boolean unsaved;
        /** The cover picture's page (-1: none), and each page's picture where it shows the page as it is (else null; a saver may fill the active one in). */
        final int cover;
        final ArrayList<byte[]> pictures=new ArrayList<>();
        /** Inkwash: the covers' packed bytes as the book holds them, front then back (null: nothing drawn), and their pictures. */
        final PackedPage[] covers=new PackedPage[2];
        final byte[][] coverPictures=new byte[2][];
        /** What the active page holds, for its picture made from {@link #activePage}. */
        final PictureKey activeKey;
        /** Inkwash: the covers' design. */
        final CoverDesign design;
        /** Inkwash: the notebook's other options (template, tools). */
        final NotebookOptions options;
        /** Inkwash: tracing paper (the Paper, the ghost, the steps). */
        final TracingPaper tracing;
        /** Inkwash: each page's own template key (null: none). */
        final java.util.List<String> pageTemplates;
        /** Inkwash: each page's own rules ({@link NotebookRules#own}). */
        final int[] pageRules;
        /** Inkwash: each page's own time, s (0: none). */
        final int[] pageSeconds;
        /** Inkwash: the day a page was last added to or changed (today, when one is in this snapshot; -1: not known), and the page edits it holds. */
        final long lastDay,pageEdits;
        /** The pages as {@code BookCodec} last wrote them, the active one packed, and where each one's bytes start in the file; null until written. */
        volatile java.util.List<PackedPage> written;
        volatile long[] offsets;
        /** The covers as {@code BookCodec} wrote them (null: none written), front then back, and where their bytes start. */
        final PackedPage[] writtenCovers=new PackedPage[2];
        final long[] coverOffsets=new long[2];
        /** The active page packed, once {@link #packed} packs it. */
        private PackedPage packedNow;
        private boolean activePacked;
        /** Every page as a file holds them, once {@link #filed} packs them. */
        private java.util.List<PackedPage> filed;
        private PackedPage[] filedCovers;
        private final long edits;
        private final java.util.HashMap<Integer,Long> revisions=new java.util.HashMap<>();
        Snapshot(DrawingBook book){
            unsaved=book.unsaved(); edits=book.edits;
            for(java.util.Map.Entry<Integer,ToneDocument> page:book.cache.entrySet())revisions.put(page.getKey(),page.getValue().revision());
            width=book.width;height=book.height;index=book.index;pages=new ArrayList<>(book.pages);
            canvasChoices=new ArrayList<>(book.canvasChoices);
            activePage=book.current().layerSnapshot();
            packedActivePage=book.currentPacked();
            cover=book.cover;
            for(int i=0;i<book.count();i++)pictures.add(book.picture(i));
            for(int s=0;s<2;s++){covers[s]=book.covers[s];coverPictures[s]=book.picture(coverAt(s));}
            activeKey=book.pictureKey(book.index);
            design=book.design;
            options=book.options;
            tracing=book.tracing();
            pageTemplates=book.pageTemplates();
            pageRules=book.pageRules();
            pageSeconds=book.pageSeconds();
            pageEdits=book.pageEdits;
            lastDay=book.pagesChanged()?book.today.getAsLong():book.lastDay;
        }
        /** Inkwash: page {@code i}'s template in the copies (Blank when they leave guides out). */
        NotebookTemplate printedTemplate(int i){return options.pageTemplate(pageTemplates.get(i));}
        int count(){return pages.size();}
        /** The page a file opens on: the page drawn on, or the first or last page for a cover. */
        int fileIndex(){return index==FRONT?0:index==BACK?pages.size()-1:index;}
        /** Page or cover {@code i}'s picture (null: none), and {@link #setPicture} to give it one. */
        byte[] picture(int i){return isCover(i)?coverPictures[side(i)]:pictures.get(i);}
        void setPicture(int i,byte[] picture){if(isCover(i))coverPictures[side(i)]=picture;else pictures.set(i,picture);}
        /** What {@code BookCodec} wrote for page or cover {@code i} (null: nothing, a cover with nothing drawn). */
        PackedPage writtenAt(int i){return isCover(i)?writtenCovers[side(i)]:written==null?null:written.get(i);}
        /**
         * Every page packed, as the book holds them (read only when asked for): the active one packed now if it isn't,
         * {@linkplain PackedPage#quick quickly} (Inkwash: for the recovery file; {@link #filed} for a book's file). Safe off
         * the owner's thread.
         */
        synchronized java.util.List<PackedPage> packed() throws IOException {
            ArrayList<PackedPage> all=new ArrayList<>(pages);
            PackedPage active=packActive();
            if(!isCover(index))all.set(index,active);
            return all;
        }
        /** The covers packed, as {@link #packed}: the active one now if it is a cover (null when nothing is drawn on it). */
        synchronized PackedPage[] packedCovers() throws IOException {
            PackedPage[] all=covers.clone();
            PackedPage active=packActive();
            if(isCover(index))all[side(index)]=active;
            return all;
        }
        /** The active page packed quickly if it isn't packed yet (null: a cover with nothing drawn). */
        private PackedPage packActive() throws IOException {
            if(!activePacked){
                packedNow=packedActivePage!=null?packedActivePage:isCover(index)&&activePage.unmarked()?null:PackedPage.of(encode(page(index),DocumentCodec.QUICK),true);
                activePacked=true;
            }
            return packedNow;
        }
        /** The active page as {@link #packed} packed it, or null before (or for a cover with nothing drawn). */
        synchronized PackedPage activePacked() { return packedNow; }
        /**
         * Inkwash: every page as a book's file holds them: as {@link #packed}, but each {@linkplain PackedPage#quick quick}
         * one packed at the default level, as files always held pages (the same page, the same bytes). Safe off the
         * owner's thread.
         */
        synchronized java.util.List<PackedPage> filed() throws IOException {
            if(filed!=null)return filed;
            ArrayList<PackedPage> all=new ArrayList<>(pages);
            java.util.IdentityHashMap<PackedPage,PackedPage> repacked=new java.util.IdentityHashMap<>();
            for(int i=0;i<all.size();i++) {
                PackedPage page=all.get(i);
                if(i==index)all.set(i,packedActivePage!=null&&!packedActivePage.quick?packedActivePage:PackedPage.of(encode(page(index))));
                else if(page.quick) {
                    PackedPage done=repacked.get(page);
                    if(done==null)repacked.put(page,done=PackedPage.of(encode(page(i))));
                    all.set(i,done);
                }
            }
            return filed=all;
        }
        /** Inkwash: the covers as a book's file holds them, front then back, as {@link #filed}: null for one with nothing drawn. */
        synchronized PackedPage[] filedCovers() throws IOException {
            if(filedCovers!=null)return filedCovers;
            PackedPage[] all=covers.clone();
            for(int s=0;s<2;s++) {
                int at=coverAt(s);
                if(at==index)all[s]=packedActivePage!=null&&!packedActivePage.quick?packedActivePage:activePage.unmarked()?null:PackedPage.of(encode(page(at)));
                else if(all[s]!=null&&all[s].quick)all[s]=PackedPage.of(encode(page(at)));
            }
            return filedCovers=all;
        }
        /** Inkwash: nothing reads this snapshot's page any more (it was written or dropped): its next write needn't copy whole layers ({@link ToneDocument.Snapshot#release}). */
        void release() { activePage.release(); }
        /** As {@link #page}, with the template's guides under it when the notebook prints them in its copies (a cover never has them). */
        ToneDocument printed(int i) throws IOException {
            ToneDocument page=page(i);
            if(!isCover(i))page.placeGuides(printedTemplate(i).shared(width,height));
            return page;
        }
        /** Decodes one page or cover without selecting it or touching the live book's undo history (a cover without its paper). */
        ToneDocument page(int i) throws IOException {
            if(i==index)return new ToneDocument(activePage);
            PackedPage packed=isCover(i)?covers[side(i)]:pages.get(i);
            if(packed==null){ ToneDocument page=new ToneDocument(width,height); if(!isCover(i))page.placePaper(options.paper); return page; }
            return DocumentCodec.read(new ByteArrayInputStream(packed.bytes()));
        }
    }
}
