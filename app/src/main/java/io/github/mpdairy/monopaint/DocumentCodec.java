package io.github.mpdairy.monopaint;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Versioned, bounded, checksummed logical document; never stores display dots. */
final class DocumentCodec {
    /**
     * Inkwash: held by a background job while it has a whole page decoded or drawn into a picture (copies, a save's
     * picture, the notebook screens' pictures, ghosts, exports), so their pages' memory doesn't add up. Never held on the
     * main thread, nor while waiting for another job.
     */
    static final Object ONE_PAGE = new Object();
    private static final int MAGIC = 0x54534d31; // TSM1
    private static final int LAYER_MAGIC = 0x54534d32; // TSM2
    private static final int OPACITY_MAGIC = 0x54534d33; // TSM3
    /** TSM3 followed by the page's masking fluid; pages without any keep writing TSM3. */
    private static final int MASK_MAGIC = 0x54534d34; // TSM4
    /**
     * TSM4's layers, then a flag and the masking fluid if set, then tagged sections (a tag, a length, its bytes; a
     * reader skips tags it doesn't know) and an end tag, all inside the checksum. Written only when a page needs a
     * section (a layer, or the masking fluid, keeping paint the eraser can't take; magnets; a shaded paper; mirrors; fading marks); others keep
     * writing TSM3 or TSM4, byte for byte.
     */
    private static final int SECTIONS_MAGIC = 0x54534d35; // TSM5
    /** A section: each layer's flag and, if set, its erasable shares ({@link ToneDocument.Layer#share}), a byte a pixel. */
    private static final int ERASABLE_TAG = 0x45524153; // ERAS
    /**
     * A section: the masking fluid's shares ({@link ToneDocument.Layer#share}), a byte a pixel: how much of it the eraser
     * can't lift (a wax resist). Written only when it keeps some; a reader without it reads the wax as masking fluid.
     */
    private static final int MASK_SHARE_TAG = 0x4d534852; // MSHR
    /**
     * Inkwash: a section: the page's fridge magnets ({@link Magnets#encode}), written only when it has some. A reader
     * without it skips it and opens the drawing without them.
     */
    private static final int WORDS_TAG = 0x574f5244; // WORD
    /**
     * Inkwash: a section: the page's paper tone ({@link ToneDocument#paperTone}), a byte (a later version may add more
     * after it), written only when the paper isn't white. A reader without it opens the page on white paper.
     */
    private static final int PAPER_TAG = 0x50415052; // PAPR
    /**
     * Inkwash: a section: each layer's flag and, if set, its print tone ({@link ToneDocument.Layer#print}: what its
     * screened marks would show unscreened, which a later screened stroke builds on), a byte a pixel. Written only when
     * some layer holds some, so every page without a screened mark writes as before, byte for byte. A reader without it
     * opens the drawing as it shows; a screened stroke drawn over it later starts its dots afresh.
     */
    private static final int PRINT_TAG = 0x50524e54; // PRNT
    /**
     * Inkwash: a section: for an eraser that takes one kind of mark, each layer's kinds ({@link ToneDocument.Layer#kinds}), a
     * byte of bits (Pen, Paint, Pencil); where it has more than one, the kinds it held before it had a plane, and the plane
     * ({@link ToneDocument.Layer#kind}), a byte a pixel. Written only when some layer has kinds, so a drawing never drawn on
     * with this version writes as before, byte for byte. A reader without it skips it: its eraser takes every kind.
     */
    private static final int KIND_TAG = 0x4b494e44; // KIND
    /**
     * Inkwash: a section: each layer's kind planes ({@link ToneDocument.Layer#planes}). A layer with any writes, in the
     * layers' part, its look ({@link ToneDocument#sheets}: base and planes as one), so a reader without the section opens
     * the page as it showed (one plane a layer, kinds lost); here, a byte: bit {@code p} set for each plane holding paint,
     * bit 4 when its base holds some (else its base is bare); then its base's tones and coverages if set, and for each
     * plane its tones and coverages ({@code w × h} each), a flag and its shares if it keeps any ({@link
     * ToneDocument.Layer#keeps}), a flag and its print tone if it has some. A layer with no planes writes 0 and its own
     * pixels as ever. Written only when a plane holds paint; a page without writes as before, byte for byte. Plane opacity
     * isn't kept.
     */
    private static final int PLANES_TAG = 0x504c4e53; // PLNS
    /**
     * Inkwash: a section: the page's mirrors ({@link Mirrors#encode}: their lines, on or off, the style and the side
     * kept), written only when it has some. A reader without it skips it and opens the drawing without them.
     */
    private static final int MIRRORS_TAG = 0x4d495252; // MIRR
    /**
     * Inkwash: a section: a timed page's clock ({@link PageClock#encode}: the drawing time spent, a flags byte), written only
     * once the page's timer has started. A reader without it skips it and opens the page unlocked.
     */
    private static final int TIME_TAG = 0x54494d45; // TIME
    /**
     * Inkwash: a section: the page's marks still fading or darkening by the clock (Fading, Lemon Juice:
     * {@link FadingInk#encode}), written only while it has some, so a page whose fading marks are gone writes as before,
     * byte for byte. A reader without it opens the page as it showed when saved, the marks staying so.
     */
    private static final int FADE_TAG = 0x46414445; // FADE
    /**
     * Inkwash: a section: the page's strokes for Replay ({@link StrokeLog#encode}), written only when it has some. A reader
     * without it skips it and opens the page without a replay.
     */
    private static final int REPLAY_TAG = 0x52504c59; // RPLY
    /**
     * Inkwash: a section: where the eraser rubbed a notebook cover's design off ({@link CoverWear#encode}), written only
     * when it has. A reader before it skips it, and shows the design whole.
     */
    private static final int WORN_TAG = 0x574f524e; // WORN
    /**
     * Inkwash: a section: the Daily Prompt card the page was given ({@link DailyPrompt#line}, UTF-8), written only when it has
     * one. A reader before it skips it and opens the page without its prompt.
     */
    private static final int PROMPT_TAG = 0x50524d54; // PRMT
    /**
     * Inkwash: a section: the page's Timed Sections ({@link TimedSections#encode}), written only once they have started. A
     * reader before it skips it and opens the page with its sections not started.
     */
    private static final int PACE_TAG = 0x53454354; // SECT
    /**
     * Inkwash: a section: Show Kinds' steps on the page ({@link ToneDocument#kindsShown}), a byte, written only when a kind
     * isn't Full. A reader before it skips it and shows every kind in full.
     */
    private static final int KINDS_SHOWN_TAG = 0x4b53484e; // KSHN
    /** After the last section. */
    private static final int END_TAG = 0;
    /** Inkwash: the deflate level for bytes only the recovery file keeps ({@link java.util.zip.Deflater#BEST_SPEED}). */
    static final int QUICK = java.util.zip.Deflater.BEST_SPEED;
    static void write(OutputStream stream, ToneDocument.Snapshot snapshot) throws IOException {
        write(stream, snapshot, java.util.zip.Deflater.DEFAULT_COMPRESSION);
    }
    /**
     * As {@link #write(OutputStream, ToneDocument.Snapshot)}, deflated at {@code level} (a {@code Deflater} level): any
     * level reads the same. Inkwash: the recovery file's pages at {@link #QUICK}, about 3.5 times faster for a few percent
     * more bytes; files keep the default level, byte for byte.
     */
    static void write(OutputStream stream, ToneDocument.Snapshot snapshot, int level) throws IOException {
        boolean keeps=false;
        for(ToneDocument.Layer layer:snapshot.layers) keeps|=layer.keeps();
        boolean prints=false;
        for(ToneDocument.Layer layer:snapshot.layers) prints|=layer.prints();
        boolean kinded=false;
        for(ToneDocument.Layer layer:snapshot.layers) kinded|=layer.kinded();
        boolean planed=false;
        for(ToneDocument.Layer layer:snapshot.layers) planed|=planes(layer)!=0;
        boolean wax=snapshot.mask!=null&&snapshot.mask.keeps(), words=!snapshot.magnets.isEmpty(), shaded=snapshot.paper!=255, mirrored=!snapshot.mirrors.isEmpty(), timed=snapshot.clock.kept(), fading=!snapshot.fades.isEmpty(), replay=!snapshot.log.isEmpty(), worn=snapshot.wear!=null, prompted=snapshot.prompt!=null, paced=snapshot.sections.started(), shown=snapshot.kindsShown!=0, sections=keeps||wax||words||shaded||prints||kinded||planed||mirrored||timed||fading||replay||worn||prompted||paced||shown;
        DataOutputStream header=new DataOutputStream(stream);
        header.writeInt(sections ? SECTIONS_MAGIC : snapshot.mask != null ? MASK_MAGIC : OPACITY_MAGIC); header.writeInt(snapshot.width); header.writeInt(snapshot.height);
        java.util.zip.Deflater deflater=new java.util.zip.Deflater(level);
        try {
            DeflaterOutputStream compressed=new DeflaterOutputStream(stream,deflater);
            CRC32 crc=new CRC32();
            DataOutputStream data=new DataOutputStream(new java.util.zip.CheckedOutputStream(compressed,crc));
            data.writeInt(snapshot.layers.size()); data.writeInt(snapshot.active);
            for(ToneDocument.Layer layer:snapshot.layers) {
                data.writeUTF(layer.name); data.writeBoolean(layer.visible); data.writeByte(layer.opacity);
                if(planes(layer)!=0) look(data,layer,snapshot.width); else { data.write(layer.tones); data.write(layer.alpha); }
            }
            if(sections) data.writeBoolean(snapshot.mask!=null);
            if(snapshot.mask!=null) { data.write(snapshot.mask.tones); data.write(snapshot.mask.alpha); }
            if(keeps) {
                int length=0;
                for(ToneDocument.Layer layer:snapshot.layers) length+=1+(layer.keeps()?layer.share.length:0);
                data.writeInt(ERASABLE_TAG); data.writeInt(length);
                for(ToneDocument.Layer layer:snapshot.layers) { boolean kept=layer.keeps(); data.writeBoolean(kept); if(kept) data.write(layer.share); }
            }
            if(wax) { data.writeInt(MASK_SHARE_TAG); data.writeInt(snapshot.mask.share.length); data.write(snapshot.mask.share); }
            if(words) { byte[] magnets=snapshot.magnets.encode(); data.writeInt(WORDS_TAG); data.writeInt(magnets.length); data.write(magnets); }
            if(shaded) { data.writeInt(PAPER_TAG); data.writeInt(1); data.writeByte(snapshot.paper); }
            if(prints) {
                int length=0;
                for(ToneDocument.Layer layer:snapshot.layers) length+=1+(layer.prints()?layer.print.length:0);
                data.writeInt(PRINT_TAG); data.writeInt(length);
                for(ToneDocument.Layer layer:snapshot.layers) { boolean printed=layer.prints(); data.writeBoolean(printed); if(printed) data.write(layer.print); }
            }
            if(kinded) {
                int length=0, pixels=snapshot.width*snapshot.height;
                for(ToneDocument.Layer layer:snapshot.layers) length+=1+(Integer.bitCount(layer.kinded()?layer.kinds&7:0)>1?1+pixels:0);
                data.writeInt(KIND_TAG); data.writeInt(length);
                for(ToneDocument.Layer layer:snapshot.layers) {
                    int kinds=layer.kinded()?layer.kinds&7:0;
                    data.writeByte(kinds);
                    if(Integer.bitCount(kinds)>1) { data.writeByte(layer.kindBase&7); if(layer.kind!=null) data.write(layer.kind); else zeros(data,pixels); }
                }
            }
            if(planed) {
                long length=0; int pixels=snapshot.width*snapshot.height;
                for(ToneDocument.Layer layer:snapshot.layers) {
                    length++;
                    int set=planes(layer);
                    if(set!=0&&layer.covers()) length+=2L*pixels;
                    for(int p=0;p<ToneDocument.PLANES;p++) if((set>>p&1)!=0) { ToneDocument.Layer plane=layer.planes[p]; length+=2L*pixels+2+(plane.keeps()?pixels:0)+(plane.prints()?pixels:0); }
                }
                data.writeInt(PLANES_TAG); data.writeInt((int)length);
                for(ToneDocument.Layer layer:snapshot.layers) {
                    int set=planes(layer);
                    boolean base=set!=0&&layer.covers();
                    data.writeByte(set|(base?BASE:0));
                    if(base) { data.write(layer.tones); data.write(layer.alpha); }
                    for(int p=0;p<ToneDocument.PLANES;p++) if((set>>p&1)!=0) {
                        ToneDocument.Layer plane=layer.planes[p];
                        data.write(plane.tones); data.write(plane.alpha);
                        boolean kept=plane.keeps(), printed=plane.prints();
                        data.writeBoolean(kept); if(kept) data.write(plane.share);
                        data.writeBoolean(printed); if(printed) data.write(plane.print);
                    }
                }
            }
            if(mirrored) { byte[] mirrors=snapshot.mirrors.encode(); data.writeInt(MIRRORS_TAG); data.writeInt(mirrors.length); data.write(mirrors); }
            if(timed) { byte[] clock=snapshot.clock.encode(); data.writeInt(TIME_TAG); data.writeInt(clock.length); data.write(clock); }
            if(fading) { byte[] fades=FadingInk.encode(snapshot.fades,snapshot.layers); data.writeInt(FADE_TAG); data.writeInt(fades.length); data.write(fades); }
            if(replay) { byte[] log=snapshot.log.encode(); data.writeInt(REPLAY_TAG); data.writeInt(log.length); data.write(log); }
            if(worn) { byte[] wear=snapshot.wear.encode(); data.writeInt(WORN_TAG); data.writeInt(wear.length); data.write(wear); }
            if(prompted) { byte[] prompt=snapshot.prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8); data.writeInt(PROMPT_TAG); data.writeInt(prompt.length); data.write(prompt); }
            if(paced) { byte[] pace=snapshot.sections.encode(); data.writeInt(PACE_TAG); data.writeInt(pace.length); data.write(pace); }
            if(shown) { data.writeInt(KINDS_SHOWN_TAG); data.writeInt(1); data.writeByte(snapshot.kindsShown); }
            if(sections) data.writeInt(END_TAG);
            data.flush(); new DataOutputStream(compressed).writeLong(crc.getValue());
            compressed.finish(); compressed.flush();
        } finally { deflater.end(); }
    }
    /** Inkwash: {@link #PLANES_TAG}'s bit for a layer whose base holds paint. */
    private static final int BASE=1<<ToneDocument.PLANES;
    /** Inkwash: {@code layer}'s look ({@link ToneDocument#sheets}), its tones then its coverages, a row at a time. */
    private static void look(DataOutputStream data,ToneDocument.Layer layer,int width) throws IOException {
        byte[] row=new byte[width];
        for(int half=0;half<2;half++) for(int at=0;at<layer.alpha.length;at+=width) {
            for(int x=0;x<width;x++) { int s=ToneDocument.sheets(layer,at+x); row[x]=(byte)(half==0?s>>>8:s); }
            data.write(row);
        }
    }
    /** Inkwash: the kind planes of {@code layer} a file keeps, a bit each: those holding paint (or print tone). */
    private static int planes(ToneDocument.Layer layer) {
        int set=0;
        for(int p=0;p<ToneDocument.PLANES;p++) { ToneDocument.Layer plane=layer.plane(p); if(plane!=null&&(plane.covers()||plane.prints())) set|=1<<p; }
        return set;
    }
    /** {@code n} zero bytes to {@code out}, a block at a time (not a page-sized array for nothing). */
    private static void zeros(OutputStream out,int n) throws IOException { byte[] zero=new byte[Math.min(n,1<<16)]; for(int left=n;left>0;left-=zero.length) out.write(zero,0,Math.min(left,zero.length)); }
    private static ToneDocument readLayers(InputStream stream,int width,int height,boolean retain,boolean hasOpacity,boolean hasMask,boolean sections) throws IOException {
        java.util.zip.Inflater inflater=new java.util.zip.Inflater();
        try {
            InflaterInputStream compressed=new InflaterInputStream(stream,inflater);
            CRC32 crc=new CRC32();
            DataInputStream data=new DataInputStream(new java.util.zip.CheckedInputStream(compressed,crc));
            int count=data.readInt(),active=data.readInt();
            if(count<1||count>ToneDocument.MAX_LAYERS||active<0||active>=count) throw new IOException("Invalid layers");
            java.util.ArrayList<ToneDocument.Layer> layers=new java.util.ArrayList<>();
            for(int i=0;i<count;i++) {
                String name=data.readUTF(); int visible=data.readUnsignedByte();
                int opacity=hasOpacity?data.readUnsignedByte():100;
                if(opacity>100)throw new IOException("Invalid layer opacity");
                if(name.trim().isEmpty()||name.length()>40||visible>1) throw new IOException("Invalid layer information");
                if(retain) {
                    byte[] tones=new byte[width*height],alpha=new byte[width*height];
                    data.readFully(tones); data.readFully(alpha);
                    ToneDocument.Layer layer=new ToneDocument.Layer(name,visible==1,tones,alpha);
                    layer.opacity=opacity; layers.add(layer);
                } else consume(data,width*height*2);
            }
            ToneDocument.Layer mask=null;
            if(sections) { int flag=data.readUnsignedByte(); if(flag>1) throw new IOException("Invalid masking fluid flag"); hasMask=flag==1; }
            if(hasMask&&retain) {
                byte[] tones=new byte[width*height],alpha=new byte[width*height];
                data.readFully(tones); data.readFully(alpha);
                mask=new ToneDocument.Layer("Masking fluid",true,tones,alpha);
            } else if(hasMask) consume(data,width*height*2);
            Magnets[] magnets={Magnets.NONE};
            Mirrors[] mirrors={Mirrors.NONE};
            int[] paper={255};
            PageClock[] clock={PageClock.NONE};
            StrokeLog[] log={StrokeLog.NONE};
            CoverWear[] wear={null};
            String[] prompt={null};
            int[] kindsShown={0};
            TimedSections[] pace={null};
            java.util.List<java.util.List<FadingInk.Mark>> fades=new java.util.ArrayList<>();
            if(sections) readSections(data,width,height,count,retain?layers:null,mask,magnets,mirrors,paper,clock,fades,log,wear,prompt,pace,kindsShown);
            long checksum=crc.getValue();
            if(new DataInputStream(compressed).readLong()!=checksum) throw new IOException("Painting checksum mismatch");
            if(compressed.read()!=-1) throw new IOException("Extra painting data");
            if(!retain) return null;
            ToneDocument document=new ToneDocument(width,height,layers,active,mask);
            document.placeMagnets(magnets[0]); document.placeMirrors(mirrors[0]); document.placePaper(paper[0]); document.placeClock(clock[0]); document.placeLog(log[0]); document.placeWear(wear[0]); document.placePrompt(prompt[0]); document.placeSections(pace[0]); document.placeKindsShown(kindsShown[0]);
            if(!fades.isEmpty()) document.placeFades(fades.get(0));
            return document;
        } finally { inflater.end(); }
    }
    /**
     * TSM5's sections, to the end tag: each a tag, a length and its bytes, at most one of each known tag; a tag this
     * reader doesn't know is skipped. {@code layers} receive their shares (null: read and checked only), and
     * {@code mask} (the masking fluid, or null) its own, {@code magnets[0]} the page's magnets, {@code mirrors[0]} its
     * mirrors, {@code paper[0]} its paper tone, {@code clock[0]} its clock, {@code fades} its fading marks (with {@code layers}), {@code wear[0]} where a cover's design is rubbed off {@code prompt[0]} its prompt, {@code pace[0]} its Timed Sections and {@code kindsShown[0]} Show Kinds' steps.
     */
    private static void readSections(DataInputStream data,int width,int height,int count,java.util.ArrayList<ToneDocument.Layer> layers,ToneDocument.Layer mask,Magnets[] magnets,Mirrors[] mirrors,int[] paper,PageClock[] clock,java.util.List<java.util.List<FadingInk.Mark>> fades,StrokeLog[] log,CoverWear[] wear,String[] prompt,TimedSections[] pace,int[] kindsShown) throws IOException {
        // Inkwash: the largest section, kind planes': every layer's four, each up to four bytes a pixel.
        int pixels=width*height;
        long most=ToneDocument.MAX_LAYERS*(1+ToneDocument.PLANES*(4L*pixels+2));
        boolean erasable=false, wax=false, words=false, shaded=false, printed=false, kinded=false, planed=false, mirrored=false, timed=false, fading=false, replay=false;
        for(int sections=0;;sections++) {
            if(sections>64) throw new IOException("Too many painting sections");
            int tag=data.readInt();
            if(tag==END_TAG) return;
            int length=data.readInt();
            if(length<0||length>most) throw new IOException("Invalid painting section");
            if(tag==MASK_SHARE_TAG) {
                if(wax) throw new IOException("Repeated painting section");
                wax=true;
                if(length!=pixels) throw new IOException("Invalid masking fluid section");
                if(mask==null) { consume(data,length); continue; }
                byte[] share=new byte[pixels]; data.readFully(share); mask.share=share;
                continue;
            }
            if(tag==WORDS_TAG) {
                if(words) throw new IOException("Repeated painting section");
                words=true;
                if(length>Magnets.MOST_BYTES) throw new IOException("Invalid magnet section");
                byte[] section=new byte[length]; data.readFully(section);
                magnets[0]=Magnets.decode(section,width,height);
                continue;
            }
            if(tag==MIRRORS_TAG) {
                if(mirrored) throw new IOException("Repeated painting section");
                mirrored=true;
                if(length>Mirrors.MOST_BYTES) throw new IOException("Invalid mirror section");
                byte[] section=new byte[length]; data.readFully(section);
                mirrors[0]=Mirrors.decode(section,width,height);
                continue;
            }
            if(tag==TIME_TAG) {
                if(timed) throw new IOException("Repeated painting section");
                timed=true;
                if(length<PageClock.BYTES||length>64) throw new IOException("Invalid clock section");
                byte[] section=new byte[length]; data.readFully(section);
                PageClock read=PageClock.decode(section,length);
                if(read==null) throw new IOException("Invalid clock section");
                clock[0]=read;
                continue;
            }
            if(tag==REPLAY_TAG) {
                if(replay) throw new IOException("Repeated painting section");
                replay=true;
                if(length>StrokeLog.MOST_BYTES) throw new IOException("Invalid replay section");
                byte[] section=new byte[length]; data.readFully(section);
                StrokeLog read=StrokeLog.decode(section);
                if(read!=null) log[0]=read; // one that doesn't read is left out: the page opens without a replay
                continue;
            }
            if(tag==FADE_TAG) {
                if(fading) throw new IOException("Repeated painting section");
                fading=true;
                if(length>(ToneDocument.MOST_FADING+1L)*pixels+(long)FadingInk.MOST_MARKS*64) throw new IOException("Invalid fading section");
                if(layers==null) { consume(data,length); continue; }
                byte[] section=new byte[length]; data.readFully(section);
                fades.add(FadingInk.decode(section,width,height,layers));
                continue;
            }
            if(tag==PACE_TAG) {
                if(pace[0]!=null) throw new IOException("Repeated painting section");
                if(length<1||length>1+12*TimedSections.MOST+1024) { consume(data,length); continue; } // not a record this reads: the page opens without it
                byte[] section=new byte[length]; data.readFully(section);
                pace[0]=TimedSections.decode(section,length);
                if(pace[0]==null) pace[0]=TimedSections.NONE;
                continue;
            }
            if(tag==KINDS_SHOWN_TAG) {
                if(length!=1) { consume(data,length); continue; } // not a record this reads: every kind in full
                kindsShown[0]=data.readUnsignedByte();
                continue;
            }
            if(tag==PROMPT_TAG) {
                if(prompt[0]!=null) throw new IOException("Repeated painting section");
                if(length<1||length>DailyPrompt.MOST_BYTES) { consume(data,length); continue; } // too long to be a card: the page opens without it
                byte[] section=new byte[length]; data.readFully(section);
                prompt[0]=new String(section,java.nio.charset.StandardCharsets.UTF_8);
                continue;
            }
            if(tag==WORN_TAG) {
                if(wear[0]!=null) throw new IOException("Repeated painting section");
                if(length<1||length>pixels/8+1024) throw new IOException("Invalid wear section");
                byte[] section=new byte[length]; data.readFully(section);
                wear[0]=CoverWear.decode(section,0,width,height);
                continue;
            }
            if(tag==PAPER_TAG) {
                if(shaded) throw new IOException("Repeated painting section");
                shaded=true;
                if(length<1||length>64) throw new IOException("Invalid paper section");
                paper[0]=data.readUnsignedByte(); consume(data,length-1);
                continue;
            }
            if(tag==PLANES_TAG) {
                if(planed) throw new IOException("Repeated painting section");
                planed=true;
                long left=length;
                for(int i=0;i<count;i++) {
                    if(--left<0) throw new IOException("Short plane section");
                    int set=data.readUnsignedByte();
                    if(set>=BASE<<1||set==BASE) throw new IOException("Invalid plane section");
                    ToneDocument.Layer layer=layers==null?null:layers.get(i);
                    // A layer with planes: the layers' part held its look; its base is here, or bare.
                    if(set!=0) {
                        byte[] tones=layer==null?null:ToneDocument.paper(width,height), alpha=layer==null?null:new byte[pixels];
                        if((set&BASE)!=0) {
                            if((left-=2L*pixels)<0) throw new IOException("Short plane section");
                            if(layer==null) consume(data,2*pixels); else { data.readFully(tones); data.readFully(alpha); }
                        }
                        if(layer!=null) { layer.tones=tones; layer.alpha=alpha; }
                    }
                    for(int p=0;p<ToneDocument.PLANES;p++) {
                        if((set>>p&1)==0) continue;
                        if((left-=2L*pixels+1)<0) throw new IOException("Short plane section");
                        byte[] tones=layer==null?null:new byte[pixels], alpha=layer==null?null:new byte[pixels];
                        if(layer==null) consume(data,2*pixels); else { data.readFully(tones); data.readFully(alpha); }
                        byte[] share=flagged(data,pixels,layer!=null), print;
                        if(share!=null&&(left-=pixels)<0) throw new IOException("Short plane section");
                        if(--left<0) throw new IOException("Short plane section");
                        print=flagged(data,pixels,layer!=null);
                        if(print!=null&&(left-=pixels)<0) throw new IOException("Short plane section");
                        if(layer==null) continue;
                        ToneDocument.Layer plane=new ToneDocument.Layer(ToneDocument.planeName(p),true,tones,alpha);
                        plane.kinds=(byte)ToneDocument.PLANE_KINDS[p]; plane.share=share; plane.print=print;
                        if(layer.planes==null) layer.planes=new ToneDocument.Layer[ToneDocument.PLANES];
                        layer.planes[p]=plane;
                    }
                }
                if(left!=0) throw new IOException("Invalid plane section");
                continue;
            }
            if(tag==KIND_TAG) {
                if(kinded) throw new IOException("Repeated painting section");
                kinded=true;
                int left=length;
                for(int i=0;i<count;i++) {
                    if(--left<0) throw new IOException("Short kind section");
                    int kinds=data.readUnsignedByte();
                    if(kinds>7) throw new IOException("Invalid kind section");
                    int base=0;
                    if(Integer.bitCount(kinds)>1) {
                        if((left-=1+pixels)<0) throw new IOException("Short kind section");
                        base=data.readUnsignedByte();
                        if(base<1||base>7) throw new IOException("Invalid kind section");
                    }
                    if(layers==null) { if(base!=0) consume(data,pixels); continue; }
                    ToneDocument.Layer layer=layers.get(i);
                    layer.kinds=(byte)kinds;
                    if(base!=0) { layer.kindBase=(byte)base; layer.kind=new byte[pixels]; data.readFully(layer.kind); }
                }
                if(left!=0) throw new IOException("Invalid kind section");
                continue;
            }
            if(tag!=ERASABLE_TAG&&tag!=PRINT_TAG) { consume(data,length); continue; }
            boolean print=tag==PRINT_TAG;
            if(print?printed:erasable) throw new IOException("Repeated painting section");
            if(print) printed=true; else erasable=true;
            byte[][] planes=perLayer(data,length,count,pixels,layers!=null,print?"print":"erasable");
            if(layers!=null) for(int i=0;i<count;i++) if(planes[i]!=null) { if(print) layers.get(i).print=planes[i]; else layers.get(i).share=planes[i]; }
        }
    }
    /** Inkwash: a flag byte and, if set, a byte a pixel ({@link #PLANES_TAG}'s shares and print tone); null when unset, an empty array when set and not {@code retain}ed. */
    private static byte[] flagged(DataInputStream data,int pixels,boolean retain) throws IOException {
        int flag=data.readUnsignedByte();
        if(flag>1) throw new IOException("Invalid plane section");
        if(flag==0) return null;
        if(!retain) { consume(data,pixels); return new byte[0]; }
        byte[] plane=new byte[pixels]; data.readFully(plane); return plane;
    }
    /**
     * A per-layer section ({@link #ERASABLE_TAG}, {@link #PRINT_TAG}) of {@code length} bytes: each of {@code count}
     * layers' flag and, if set, its byte a pixel; each layer's (null where unset; all null without {@code retain}, read and checked only).
     */
    private static byte[][] perLayer(DataInputStream data,int length,int count,int pixels,boolean retain,String what) throws IOException {
        byte[][] planes=new byte[count][];
        int left=length;
        for(int i=0;i<count;i++) {
            if(--left<0) throw new IOException("Short "+what+" section");
            int flag=data.readUnsignedByte();
            if(flag>1) throw new IOException("Invalid "+what+" section");
            if(flag==0) continue;
            if((left-=pixels)<0) throw new IOException("Short "+what+" section");
            if(!retain) { consume(data,pixels); continue; }
            planes[i]=new byte[pixels]; data.readFully(planes[i]);
        }
        if(left!=0) throw new IOException("Invalid "+what+" section");
        return planes;
    }
    static void write(OutputStream stream, int width, int height, byte[] tones) throws IOException {
        ToneDocument.validateSize(width, height);
        if (tones.length != width * height) throw new IOException("Wrong tone count");
        DataOutputStream header = new DataOutputStream(stream);
        CRC32 crc = new CRC32(); crc.update(tones);
        header.writeInt(MAGIC); header.writeInt(width); header.writeInt(height); header.writeLong(crc.getValue());
        java.util.zip.Deflater deflater=new java.util.zip.Deflater();
        try {
            DeflaterOutputStream compressed = new DeflaterOutputStream(stream,deflater);
            compressed.write(tones); compressed.finish(); compressed.flush();
        } finally {deflater.end();}
    }
    static ToneDocument read(InputStream stream) throws IOException { return read(stream,true,0,0); }
    static void validate(InputStream stream,int width,int height) throws IOException { read(stream,false,width,height); }
    /**
     * Checks a page's header without inflating it: a format this reader knows and a valid size ({@code width} ×
     * {@code height} unless 0), as {@link #read} would refuse them. Opening a book checks its other pages this way.
     */
    static void checkHeader(byte[] page,int width,int height) throws IOException {
        if(page.length<12) throw new IOException("Invalid painting");
        java.nio.ByteBuffer header=java.nio.ByteBuffer.wrap(page);
        int magic=header.getInt(), w=header.getInt(), h=header.getInt();
        if (magic != MAGIC && magic != LAYER_MAGIC && magic != OPACITY_MAGIC && magic != MASK_MAGIC && magic != SECTIONS_MAGIC) throw new IOException("Unknown painting format");
        if(!ToneDocument.validSize(w,h)) throw new IOException("Invalid painting dimensions");
        if(width!=0&&(w!=width||h!=height)) throw new IOException("Page size mismatch");
    }
    private static void consume(DataInputStream input,int count) throws IOException {
        byte[] buffer=new byte[Math.min(count,8192)];
        while(count>0) { int chunk=Math.min(count,buffer.length); input.readFully(buffer,0,chunk); count-=chunk; }
    }
    private static ToneDocument read(InputStream stream,boolean retain,int expectedWidth,int expectedHeight) throws IOException {
        DataInputStream header = new DataInputStream(stream);
        int magic=header.readInt();
        if (magic != MAGIC && magic != LAYER_MAGIC && magic != OPACITY_MAGIC && magic != MASK_MAGIC && magic != SECTIONS_MAGIC) throw new IOException("Unknown painting format");
        int width = header.readInt(), height = header.readInt();
        try { ToneDocument.validateSize(width, height); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid painting dimensions", invalid); }
        if(expectedWidth!=0&&(width!=expectedWidth||height!=expectedHeight)) throw new IOException("Page size mismatch");
        if(magic==LAYER_MAGIC || magic==OPACITY_MAGIC || magic==MASK_MAGIC || magic==SECTIONS_MAGIC)
            return readLayers(stream,width,height,retain,magic!=LAYER_MAGIC,magic==MASK_MAGIC,magic==SECTIONS_MAGIC);
        long checksum=header.readLong();
        byte[] tones = retain?new byte[width * height]:null;
        CRC32 crc = new CRC32();
        java.util.zip.Inflater inflater=new java.util.zip.Inflater();
        try {
            DataInputStream compressed = new DataInputStream(new java.util.zip.CheckedInputStream(new InflaterInputStream(stream,inflater),crc));
            if(retain) compressed.readFully(tones); else consume(compressed,width*height);
            if (compressed.read() != -1) throw new IOException("Extra painting data");
        } finally {inflater.end();}
        if (crc.getValue() != checksum) throw new IOException("Painting checksum mismatch");
        if(!retain) return null;
        byte[] alpha=new byte[width*height]; java.util.Arrays.fill(alpha,(byte)255);
        java.util.ArrayList<ToneDocument.Layer> layers=new java.util.ArrayList<>();
        layers.add(new ToneDocument.Layer("Layer 1",true,tones,alpha));
        return new ToneDocument(width,height,layers,0);
    }
    private DocumentCodec() {}
}
