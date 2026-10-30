package io.github.mpdairy.monopaint;

import java.io.*;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.*;

/**
 * One ZIP-based .tsm file: a manifest and ordered, checksummed logical-tone pages, then Inkwash's notebook block
 * ({@link BookPictures}). Every page is a stored entry, so a book opened from its file ({@link #open}) reads a page's
 * bytes when they are needed ({@link PackedPage}), each checked against its CRC-32 then, and decodes only the page shown;
 * opening checks the file's structure and every page's header. A save streams the pages it didn't change from the old
 * file into the new one ({@link #write}).
 */
final class BookCodec {
    private static final int MAGIC=0x54534231, PAGE_SIZES_MAGIC=0x54534232;
    private static final int MAX_PAGE_BYTES=DrawingBook.MAX_PACKED_BYTES;
    /**
     * Writes {@code snapshot}: the manifest, its pages as stored entries, then the notebook block ({@link BookPictures}:
     * the cover and the pages' pictures), then the central directory, which lists the manifest and the pages only.
     * Written by hand rather than by {@code ZipOutputStream}, which can't put bytes before its central directory. Sets
     * {@code snapshot.written} and {@code snapshot.offsets}: the pages as written and where each one's bytes start.
     */
    static void write(OutputStream output,DrawingBook.Snapshot snapshot) throws IOException {
        java.util.List<PackedPage> pages=snapshot.filed();
        int[] choices=new int[snapshot.canvasChoices.size()];
        for(int i=0;i<choices.length;i++)choices[i]=snapshot.canvasChoices.get(i);
        // Inkwash: the covers something is drawn on, their pictures, the cover picture's page and their design (BookOptions).
        BookOptions.Covers covers=new BookOptions.Covers();
        PackedPage[] filed=snapshot.filedCovers();
        for(int s=0;s<2;s++){covers.pages[s]=filed[s];covers.pictures[s]=filed[s]==null?null:snapshot.coverPictures[s];}
        covers.picture=snapshot.cover;covers.design=snapshot.design;covers.options=snapshot.options;covers.tracing=snapshot.tracing;covers.pageTemplates=snapshot.pageTemplates;covers.pageRules=snapshot.pageRules;covers.pageSeconds=snapshot.pageSeconds;covers.lastDay=snapshot.lastDay;
        long[] coverOffsets=new long[2];
        long[] offsets=write(output,snapshot.width,snapshot.height,pages,snapshot.fileIndex(),choices,snapshot.cover,snapshot.pictures,covers,coverOffsets);
        for(int s=0;s<2;s++){snapshot.writtenCovers[s]=filed[s];snapshot.coverOffsets[s]=coverOffsets[s];}
        snapshot.written=pages; snapshot.offsets=offsets;
    }
    /**
     * Writes a book of {@code pages}, {@code index} the page it opens on, each page's canvas choice, the cover and the
     * pictures (null where none), as {@link #write(OutputStream, DrawingBook.Snapshot)}; a page stored in a file is
     * copied from it as it is, never held whole. Returns where each page's bytes start in what was written.
     */
    static long[] write(OutputStream output,int width,int height,java.util.List<PackedPage> pages,int index,int[] choices,int cover,java.util.List<byte[]> pictures) throws IOException {
        return write(output,width,height,pages,index,choices,cover,pictures,new BookOptions.Covers(),new long[2]);
    }
    /**
     * As {@link #write(OutputStream, int, int, java.util.List, int, int[], int, java.util.List)}, with Inkwash's notebook
     * covers ({@link BookOptions}, before the notebook block; nothing when nothing is drawn on them and no page is the
     * cover picture), where each cover's bytes start put in {@code coverOffsets}. The notebook block's cover is the cover
     * picture's page, or the first page for none: what readers before covers show on their shelf.
     */
    static long[] write(OutputStream output,int width,int height,java.util.List<PackedPage> pages,int index,int[] choices,int cover,java.util.List<byte[]> pictures,
            BookOptions.Covers covers,long[] coverOffsets) throws IOException {
        long size=0;for(PackedPage page:pages)size+=page.length;
        for(PackedPage page:covers.pages)if(page!=null)size+=page.length;
        if(size>DrawingBook.MAX_PACKED_BYTES)throw new IOException(DrawingBook.FULL);
        cover=Math.max(0,cover);
        if(pages.isEmpty()||pages.size()>DrawingBook.MAX_PAGES||index<0||index>=pages.size()||choices.length!=pages.size())throw new IOException("Invalid page list");
        ByteArrayOutputStream manifest=new ByteArrayOutputStream();DataOutputStream info=new DataOutputStream(manifest);
        info.writeInt(PAGE_SIZES_MAGIC);info.writeInt(width);info.writeInt(height);info.writeInt(pages.size());info.writeInt(index);
        for(int choice:choices)info.writeByte(choice);
        ArrayList<String> names=new ArrayList<>();ArrayList<PackedPage> entries=new ArrayList<>();
        names.add("manifest");entries.add(PackedPage.of(manifest.toByteArray()));
        for(int i=0;i<pages.size();i++){names.add("pages/"+(i+1)+".tsm");entries.add(pages.get(i));}
        long zipBytes=22;
        long[] crcs=new long[pages.size()];
        for(int i=0;i<entries.size();i++) zipBytes+=30+46+2L*names.get(i).length()+entries.get(i).length;
        for(int i=0;i<pages.size();i++)crcs[i]=pages.get(i).crc;
        long options=BookOptions.length(covers);
        byte[] block=BookPictures.block(cover,crcs,pictures,MAX_ARCHIVE_BYTES-zipBytes-options);
        long offset=0;long[] offsets=new long[entries.size()];
        for(int i=0;i<entries.size();i++) {
            PackedPage entry=entries.get(i);
            byte[] local=header(0x04034b50,30,names.get(i),entry.crc,entry.length,0);
            offsets[i]=offset;output.write(local,0,local.length);offset+=local.length;
            entry.copyTo(output);offset+=entry.length;
        }
        long[] at=BookOptions.write(output,offset,covers);offset+=options;
        coverOffsets[0]=at[0];coverOffsets[1]=at[1];
        output.write(block,0,block.length);offset+=block.length;
        long directory=offset;
        for(int i=0;i<entries.size();i++) {
            byte[] central=header(0x02014b50,46,names.get(i),entries.get(i).crc,entries.get(i).length,offsets[i]);
            output.write(central,0,central.length);offset+=central.length;
        }
        ByteBuffer end=ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        end.putInt(0x06054b50).putShort((short)0).putShort((short)0).putShort((short)entries.size()).putShort((short)entries.size())
                .putInt((int)(offset-directory)).putInt((int)directory).putShort((short)0);
        output.write(end.array(),0,22);output.flush();
        long[] data=new long[pages.size()];
        for(int i=0;i<data.length;i++)data[i]=offsets[i+1]+30+names.get(i+1).length();
        return data;
    }
    /** Puts a file in place: an atomic save's last step. */
    interface Rename { void run() throws IOException; }
    /**
     * The end of a save of {@code snapshot} to {@code file}: {@code rename} puts the new file in place, then every page it
     * wrote is read from it (the in-memory ones' memory freed), both while holding {@link PackedPage#LOCK}, so no read
     * meets the old file once it is gone.
     */
    static void replaced(DrawingBook.Snapshot snapshot,File file,Rename rename) throws IOException {
        synchronized(PackedPage.LOCK) {
            rename.run();
            PackedPage.Source written=PackedPage.Source.open(file);
            for(int i=0;i<snapshot.written.size();i++)snapshot.written.get(i).storedIn(written,snapshot.offsets[i]);
            for(int s=0;s<2;s++)if(snapshot.writtenCovers[s]!=null)snapshot.writtenCovers[s].storedIn(written,snapshot.coverOffsets[s]);
        }
    }
    /**
     * The most a book's file may hold: its pages' limit and 1 MB for its manifest, directory and pictures. Readers before
     * 2026-10-18 took 16 MB + 64 KB in all: a notebook past that opens only in this version on.
     */
    static final long MAX_ARCHIVE_BYTES=DrawingBook.MAX_PACKED_BYTES+(1<<20);
    /** A stored entry's local header ({@code length} 30) or its central directory record (46, at {@code offset}), with its name. */
    private static byte[] header(int signature,int length,String entry,long crc,int size,long offset) {
        byte[] name=entry.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        ByteBuffer b=ByteBuffer.allocate(length+name.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(signature);
        if(length==46)b.putShort((short)10);
        // Version 1.0, no flags, stored, 1980-01-01 00:00 (a fixed time: the same book makes the same bytes).
        b.putShort((short)10).putShort((short)0).putShort((short)0).putShort((short)0).putShort((short)0x21)
                .putInt((int)crc).putInt(size).putInt(size).putShort((short)name.length).putShort((short)0);
        if(length==46)b.putShort((short)0).putShort((short)0).putShort((short)0).putInt(0).putInt((int)offset);
        b.put(name);return b.array();
    }

    // Reading

    /** A book read whole from {@code input} (a recovery file's, a check's), its pages held in memory. */
    static DrawingBook read(InputStream input) throws IOException { return book(readPages(input)); }
    /** A book opened from its file: its pages read from it when needed ({@link #openPages}), the shown one decoded now. */
    static DrawingBook open(File file) throws IOException { return book(openPages(file)); }
    private static DrawingBook book(Pages read) throws IOException {
        if(read.single)return new DrawingBook(DocumentCodec.read(new ByteArrayInputStream(read.pages.get(0).bytes())));
        DrawingBook book=new DrawingBook(read.width,read.height,read.pages,read.active,DocumentCodec.read(new ByteArrayInputStream(read.pages.get(read.active).bytes())));
        book.restoreCanvasChoices(read.choices);
        if(read.found!=null)book.restorePictures(read.found.pictures,read.picture());
        book.restoreCovers(read.covers.pages,read.covers.pictures);
        book.restoreDesign(read.covers.design);
        book.restoreOptions(read.covers.options);
        book.restoreTracing(read.covers.tracing);
        book.restorePageTemplates(read.covers.pageTemplates);
        book.restorePageRules(read.covers.pageRules);
        book.restorePageSeconds(read.covers.pageSeconds);
        book.restoreLastDay(read.covers.lastDay);
        return book;
    }
    /** A book's file as read, nothing decoded: its pages packed (in memory, or where they are in the file), each one's header checked. */
    static final class Pages {
        final int width,height,active;
        final ArrayList<PackedPage> pages;
        final int[] choices;
        /** An older one-page file (TSM, not a ZIP): its one page is the whole file. */
        final boolean single;
        /** The notebook block, or null. */
        final BookPictures.Found found;
        /** Inkwash: the covers its options block keeps (none without one, {@link BookOptions}). */
        final BookOptions.Covers covers;
        /** Whether it has an options block. */
        final boolean options;
        Pages(int width,int height,ArrayList<PackedPage> pages,int active,int[] choices,boolean single,BookPictures.Found found) {
            this(width,height,pages,active,choices,single,found,null);
        }
        Pages(int width,int height,ArrayList<PackedPage> pages,int active,int[] choices,boolean single,BookPictures.Found found,BookOptions.Covers covers) {
            this.width=width;this.height=height;this.pages=pages;this.active=active;this.choices=choices;this.single=single;this.found=found;
            this.options=covers!=null;this.covers=covers!=null?covers:new BookOptions.Covers();
        }
        /**
         * The cover picture's page (-1: none): the options block's; without one, the notebook block's cover when it isn't
         * the first page (a page chosen as the cover before covers; the first was everyone's).
         */
        int picture() { return options?covers.picture:found!=null&&found.cover>0?found.cover:-1; }
    }
    /** A manifest's fields, checked. */
    private static final class Manifest {
        int magic,width,height,count,active; int[] choices;
    }
    private static Manifest manifest(byte[] header) throws IOException {
        if(header.length<20)throw new IOException("Invalid page manifest");
        DataInputStream info=new DataInputStream(new ByteArrayInputStream(header));
        Manifest m=new Manifest();
        m.magic=info.readInt();
        if(m.magic!=MAGIC && m.magic!=PAGE_SIZES_MAGIC)throw new IOException("Unknown painting format");
        m.width=info.readInt();m.height=info.readInt();m.count=info.readInt();m.active=info.readInt();
        try{ToneDocument.validateSize(m.width,m.height);}catch(IllegalArgumentException e){throw new IOException("Invalid page dimensions",e);}
        if(m.count<1||m.count>DrawingBook.MAX_PAGES||m.active<0||m.active>=m.count)throw new IOException("Invalid page count");
        if(header.length!=(m.magic==MAGIC?20:20+m.count))throw new IOException("Invalid page manifest");
        m.choices=new int[m.count];
        if(m.magic==PAGE_SIZES_MAGIC)for(int i=0;i<m.count;i++) {
            m.choices[i]=info.readUnsignedByte();
            if(m.choices[i]>2)throw new IOException("Invalid canvas choice");
        }
        return m;
    }
    /** Reads a book's whole file from {@code input} as {@link #read} does, every check but decoding a page. */
    static Pages readPages(InputStream input) throws IOException {
        byte[] archive=bounded(input,(int)MAX_ARCHIVE_BYTES);
        if(archive.length>=4&&archive[0]=='T'&&archive[1]=='S'&&archive[2]=='M'&&(archive[3]>='1'&&archive[3]<='5')) {
            DocumentCodec.checkHeader(archive,0,0);
            ArrayList<PackedPage> one=new ArrayList<>();one.add(PackedPage.of(archive));
            java.nio.ByteBuffer size=java.nio.ByteBuffer.wrap(archive,4,8);
            return new Pages(size.getInt(),size.getInt(),one,0,new int[1],true,null);
        }
        // Require the complete central-directory footer, including when local entries are intact.
        int end=archive.length-22;
        if(end<0||archive[end]!=0x50||archive[end+1]!=0x4b||archive[end+2]!=5||archive[end+3]!=6
                ||archive[end+20]!=0||archive[end+21]!=0)throw new IOException("Incomplete painting archive");
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry=zip.getNextEntry();
            if(entry==null||!entry.getName().equals("manifest"))throw new IOException("Missing page manifest");
            Manifest m=manifest(bounded(zip,20+DrawingBook.MAX_PAGES));
            int entries=(archive[end+10]&255)|((archive[end+11]&255)<<8);
            if(entries!=m.count+1)throw new IOException("Incomplete page directory");
            ArrayList<PackedPage> pages=new ArrayList<>();long total=0;long[] crcs=new long[m.count];
            for(int i=0;i<m.count;i++) {
                entry=zip.getNextEntry();
                if(entry==null||!entry.getName().equals("pages/"+(i+1)+".tsm"))throw new IOException("Missing or unordered page");
                // ZipInputStream checks each stored entry's CRC-32 as it reads to its end: a damaged byte is refused here.
                byte[] bytes=bounded(zip,MAX_PAGE_BYTES);total+=bytes.length;
                if(total>DrawingBook.MAX_PACKED_BYTES)throw new IOException("Painting is too large");
                PackedPage page=PackedPage.of(bytes);crcs[i]=page.crc;
                DocumentCodec.checkHeader(bytes,m.magic==MAGIC?m.width:0,m.magic==MAGIC?m.height:0);
                pages.add(page);
            }
            // The notebook block, if any, follows the last page: it isn't a local header, so there is no next entry.
            if(zip.getNextEntry()!=null)throw new IOException("Unexpected archive entry");
            long directory=(archive[end+16]&255)|(archive[end+17]&255)<<8|(archive[end+18]&255)<<16|(long)(archive[end+19]&255)<<24;
            // Inkwash: the options block, if any, just before the notebook block (or the directory).
            BookOptions.Bytes bytes=BookOptions.of(archive);
            BookOptions.Found options=BookOptions.find(bytes,0,BookPictures.start(archive,directory));
            return new Pages(m.width,m.height,pages,m.active,m.choices,false,BookPictures.parse(archive,directory,crcs),
                    options==null?null:BookOptions.covers(options,bytes,null,m.count));
        }
    }
    /**
     * Opens a book's file without reading its pages: the end record, the central directory, each entry's local header (in
     * order, stored, within the file, named as the directory says), the manifest (its CRC checked) and each page's header.
     * A page's bytes are read from the file when needed and checked against its CRC-32 then ({@link PackedPage}); an
     * older one-page file (TSM) is read whole.
     */
    static Pages openPages(File file) throws IOException {
        PackedPage.Source source=PackedPage.Source.open(file);
        try { return openPages(source); }
        catch(IOException|RuntimeException e) { source.close(); throw e instanceof IOException?(IOException)e:new IOException("Damaged painting",e); }
    }
    private static Pages openPages(PackedPage.Source source) throws IOException {
        long size=source.length();
        if(size>MAX_ARCHIVE_BYTES)throw new IOException("Painting data exceeds size limit");
        if(size>=4) {
            byte[] magic=source.read(0,4);
            if(magic[0]=='T'&&magic[1]=='S'&&magic[2]=='M'&&(magic[3]>='1'&&magic[3]<='5')) {
                byte[] all=source.read(0,(int)size);
                source.close();
                return readPages(new ByteArrayInputStream(all));
            }
        }
        if(size<22)throw new IOException("Incomplete painting archive");
        byte[] eocd=source.read(size-22,22);
        ByteBuffer e=ByteBuffer.wrap(eocd).order(ByteOrder.LITTLE_ENDIAN);
        if(e.getInt(0)!=0x06054b50||e.getShort(20)!=0)throw new IOException("Incomplete painting archive");
        int entries=e.getShort(10)&0xffff;
        long directorySize=e.getInt(12)&0xffffffffL, directory=e.getInt(16)&0xffffffffL;
        if(entries<2||entries>DrawingBook.MAX_PAGES+1||directory+directorySize!=size-22||directorySize>(1<<20))throw new IOException("Incomplete page directory");
        ByteBuffer cd=ByteBuffer.wrap(source.read(directory,(int)directorySize)).order(ByteOrder.LITTLE_ENDIAN);
        String[] names=new String[entries]; long[] crcs=new long[entries], data=new long[entries]; int[] lengths=new int[entries];
        long previousEnd=0;
        for(int i=0,at=0;i<entries;i++) {
            if(at+46>cd.capacity()||cd.getInt(at)!=0x02014b50)throw new IOException("Incomplete page directory");
            int flags=cd.getShort(at+8)&0xffff, method=cd.getShort(at+10)&0xffff;
            long crc=cd.getInt(at+16)&0xffffffffL, packed=cd.getInt(at+20)&0xffffffffL, plain=cd.getInt(at+24)&0xffffffffL;
            int nameLength=cd.getShort(at+28)&0xffff, extra=cd.getShort(at+30)&0xffff, comment=cd.getShort(at+32)&0xffff;
            long local=cd.getInt(at+42)&0xffffffffL;
            if(at+46+nameLength>cd.capacity()||method!=0||(flags&1)!=0||packed!=plain||packed>MAX_PAGE_BYTES)throw new IOException("Invalid page entry");
            byte[] name=new byte[nameLength];
            for(int k=0;k<nameLength;k++)name[k]=cd.get(at+46+k);
            names[i]=new String(name,java.nio.charset.StandardCharsets.UTF_8);
            // Its local header: where the directory says, in order, with the same name; its bytes after it, before the directory.
            if(local<previousEnd||local+30>directory)throw new IOException("Missing or unordered page");
            ByteBuffer lh=ByteBuffer.wrap(source.read(local,30)).order(ByteOrder.LITTLE_ENDIAN);
            int localName=lh.getShort(26)&0xffff, localExtra=lh.getShort(28)&0xffff;
            if(lh.getInt(0)!=0x04034b50||(lh.getShort(8)&0xffff)!=0||localName!=nameLength)throw new IOException("Missing or unordered page");
            if(!java.util.Arrays.equals(source.read(local+30,nameLength),name))throw new IOException("Missing or unordered page");
            data[i]=local+30+localName+localExtra; lengths[i]=(int)packed; crcs[i]=crc;
            previousEnd=data[i]+packed;
            if(previousEnd>directory)throw new IOException("Incomplete painting archive");
            at+=46+nameLength+extra+comment;
        }
        if(!names[0].equals("manifest"))throw new IOException("Missing page manifest");
        PackedPage manifestPage=PackedPage.in(source,data[0],lengths[0],crcs[0]);
        if(lengths[0]>20+DrawingBook.MAX_PAGES)throw new IOException("Invalid page manifest");
        Manifest m=manifest(manifestPage.bytes());
        if(entries!=m.count+1)throw new IOException("Incomplete page directory");
        ArrayList<PackedPage> pages=new ArrayList<>();long total=0;long[] pageCrcs=new long[m.count];
        for(int i=0;i<m.count;i++) {
            if(!names[i+1].equals("pages/"+(i+1)+".tsm"))throw new IOException("Missing or unordered page");
            total+=lengths[i+1];
            if(total>DrawingBook.MAX_PACKED_BYTES)throw new IOException("Painting is too large");
            if(lengths[i+1]<12)throw new IOException("Invalid painting");
            DocumentCodec.checkHeader(source.read(data[i+1],12),m.magic==MAGIC?m.width:0,m.magic==MAGIC?m.height:0);
            pages.add(PackedPage.in(source,data[i+1],lengths[i+1],crcs[i+1]));pageCrcs[i]=crcs[i+1];
        }
        // The notebook block, if any, between the last page and the directory.
        BookPictures.Found found=null;
        long blockStart=directory;
        if(directory-previousEnd>=8) {
            byte[] trailer=source.read(directory-8,8);
            long length=BookPictures.readInt(trailer,0)&0xffffffffL;
            if(BookPictures.readInt(trailer,4)==BookPictures.MAGIC&&length>=16&&directory-8-length>=previousEnd) {
                byte[] block=source.read(directory-8-length,(int)length+8);
                found=BookPictures.parse(block,block.length,pageCrcs);
                blockStart=directory-8-length;
            }
        }
        // Inkwash: the options block, if any, just before it (the covers' pages read from the file when needed).
        BookOptions.Bytes bytes=BookOptions.of(source);
        BookOptions.Found options=BookOptions.find(bytes,previousEnd,blockStart);
        return new Pages(m.width,m.height,pages,m.active,m.choices,false,found,options==null?null:BookOptions.covers(options,bytes,source,m.count));
    }
    /** Decodes only the first page, for previews; skips the rest of the book. */
    static ToneDocument readFirstPage(InputStream input) throws IOException {
        BufferedInputStream buffered=new BufferedInputStream(input);buffered.mark(4);
        byte[] magic=new byte[4];int read=0,n;
        while(read<4&&(n=buffered.read(magic,read,4-read))>0)read+=n;
        buffered.reset();
        if(read==4&&magic[0]=='T'&&magic[1]=='S'&&magic[2]=='M'&&(magic[3]>='1'&&magic[3]<='5'))
            return DocumentCodec.read(buffered);
        ZipInputStream zip=new ZipInputStream(buffered);
        for(ZipEntry entry;(entry=zip.getNextEntry())!=null;)
            if(entry.getName().equals("pages/1.tsm"))
                return DocumentCodec.read(new ByteArrayInputStream(bounded(zip,MAX_PAGE_BYTES)));
        throw new IOException("Missing first page");
    }
    private static byte[] bounded(InputStream input,int limit) throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int read;
        while((read=input.read(buffer))!=-1) {
            if(output.size()+read>limit)throw new IOException("Painting data exceeds size limit");output.write(buffer,0,read);
        }
        return output.toByteArray();
    }
    private BookCodec(){}
}
