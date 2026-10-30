package io.github.mpdairy.monopaint;

import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Uses NomadUiChecks' disposable book and the outer suite's session restoration. */
final class PageNavigationChecks {
    static void run(Instrumentation test,PaintActivity app,StringBuilder report)throws Exception {
        run(test,app,report,false);
    }
    static void runFeedback(Instrumentation test,PaintActivity app,StringBuilder report)throws Exception {
        run(test,app,report,true);
    }
    private static void run(Instrumentation test,PaintActivity app,StringBuilder report,boolean feedbackOnly)throws Exception {
        if(!feedbackOnly) {verifyRaster(report);benchmark(test,app,report);}
        if(feedbackOnly)return;
        // Retired: MonoPaint's header page row, Nomad Pages popup and page overview grid. Inkwash never shows that
        // header (PaintActivity.showInkWashBar); pages turn from its page bar (PageTurner), checked on the desktop.
        DrawingBook book=new DrawingBook(new ToneDocument(640,720));
        book.current().begin();for(int y=80;y<260;y++)book.current().paintSpan(70,250,y,40);book.current().finish();
        book.addPage();book.current().begin();for(int y=100;y<380;y++)book.current().paintSpan(90,400,y,160);book.current().finish();
        main(test,() -> call(app,"replaceBook",new Class<?>[]{DrawingBook.class},book));idle(test);
        for(int target:new int[]{0,1,0}) {
            main(test,() -> {
                int artwork=(Integer)get(get(app,"pad"),"pagePresentCount");
                call(app,"changePage",new Class<?>[]{int.class},target);
                check(book.index()==target,"Page turn selects the page");
                check((Integer)get(get(app,"pad"),"pagePresentCount")>artwork,"Page turn submits its artwork");
            });idle(test);
        }
        check(book.current().tone(70,80)==40,"Turning back keeps the first page's artwork");
        // The page bar's + tile (the owner, 2026-10-24): a new page at the notebook's end, turned to (the tile's tap is PaintActivity.newPage).
        main(test,() -> call(app,"newPage",new Class<?>[0]));idle(test);
        check(book.count()==3&&book.index()==2&&BlankPages.blank(book,2)&&app.pad.document==book.current(),"+ adds a page at the end and turns to it");
        main(test,() -> call(app,"changePage",new Class<?>[]{int.class},1));idle(test);
        check(book.current().tone(90,100)==160,"The page before it stays");
        // An old notebook's blank page at its end goes when it is opened; the page drawn on stays.
        DrawingBook old=new DrawingBook(new ToneDocument(640,720));
        old.current().begin();for(int y=80;y<260;y++)old.current().paintSpan(70,250,y,40);old.current().finish();
        old.addPage();old.select(0);
        main(test,() -> call(app,"replaceBook",new Class<?>[]{DrawingBook.class},old));idle(test);
        check(old.count()==1&&old.index()==0,"Opened: the blank page at its end goes");
        report.append("PASS: Page turns select, submit and keep each page's artwork; the + tile adds a page at the end; an old notebook's blank page at its end goes on opening.\n");
    }
    private static void overview(Instrumentation test,PaintActivity app,DrawingBook book,boolean nomad,boolean capture)throws Exception {
        if(nomad){main(test,() -> call(app,"showPagePanel"));idle(test);}
        View number=(View)get(app,nomad?"popupPageNumber":"pageNumber");
        tap(test,number);idle(test);
        android.app.AlertDialog dialog=(android.app.AlertDialog)get(app,"pageOverview");
        check(dialog!=null&&dialog.isShowing(),"Page number opens overview by pen touch");
        check(get(app,"pagePanel")==null,"Overview replaces compact popup");
        PageOverview grid=findGrid(dialog.getWindow().getDecorView());
        check(grid!=null&&grid.getCount()==3,"Grid contains every page");
        check(book.index()==2&&!book.current().canUndo(),"Opening previews does not select or edit a page");
        for(int i=0;i<100;i++) {
            android.util.LruCache<?,?> cache=(android.util.LruCache<?,?>)get(grid,"thumbnails");
            if(cache.size()==3)break;
            SystemClock.sleep(50);test.waitForIdleSync();
        }
        check(((android.util.LruCache<?,?>)get(grid,"thumbnails")).size()==3,"All visible thumbnails load");
        @SuppressWarnings("unchecked") android.util.LruCache<Integer,android.graphics.Bitmap> previews=
                (android.util.LruCache<Integer,android.graphics.Bitmap>)get(grid,"thumbnails");
        check(previews.get(0).getPixel(50,60)==android.graphics.Color.rgb(40,40,40),"First thumbnail shows its saved artwork");
        check(previews.get(1).getPixel(70,90)==android.graphics.Color.rgb(160,160,160),"Second thumbnail shows its own artwork");
        check(previews.get(2).getPixel(50,60)==android.graphics.Color.WHITE,"Current blank page preview stays blank");
        check(grid.getChildAt(2).isActivated(),"Current page is highlighted");
        RectF frame=bounds((View)get(app,"orientationFrame"));
        for(int i=0;i<grid.getChildCount();i++)check(frame.contains(bounds(grid.getChildAt(i))),"Rotated thumbnail stays inside app");
        if(capture)screenshot(test,app,nomad?"nomad-page-overview.png":"page-overview.png");
        tap(test,grid.getChildAt(0));idle(test);
        check(get(app,"pageOverview")==null&&book.index()==0,"Thumbnail touch jumps to selected page and closes grid");
        check(book.current().tone(70,80)==40,"Jump preserves selected page artwork");
        // Dialog exit animations and focus return are asynchronous on the tablet.
        for(int i=0;i<40&&get(get(app,"pad"),"direct")==null;i++){SystemClock.sleep(50);test.waitForIdleSync();}
        check(get(get(app,"pad"),"direct")!=null,"Fast drawing display reconnects after selecting thumbnail; focus="+app.hasWindowFocus());
        main(test,() -> call(app,"showPageOverview"));idle(test);
        dialog=(android.app.AlertDialog)get(app,"pageOverview");
        test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);idle(test);
        check(!dialog.isShowing()&&book.index()==0,"Back closes overview without changing pages");
    }
    private static void overviewScroll(Instrumentation test,PaintActivity app)throws Exception {
        DrawingBook book=new DrawingBook(new ToneDocument(48,64));
        for(int i=1;i<DrawingBook.MAX_PAGES;i++)book.addPage();
        main(test,() -> {call(app,"replaceBook",new Class<?>[]{DrawingBook.class},book);call(app,"showPageOverview");});idle(test);
        PageOverview grid=findGrid(((android.app.AlertDialog)get(app,"pageOverview")).getWindow().getDecorView());
        check(grid.getCount()==100&&grid.getLastVisiblePosition()==99,"Long overview opens scrolled to current page");
        main(test,() -> grid.setSelection(0));idle(test);
        tap(test,grid.getChildAt(0));idle(test);check(book.index()==0,"Can scroll and jump across a 100-page book");
    }
    private static PageOverview findGrid(View view) {
        if(view instanceof PageOverview)return (PageOverview)view;
        if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++) {
            PageOverview found=findGrid(((android.view.ViewGroup)view).getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
    private static void tap(Instrumentation test,View view) {
        RectF area=bounds(view);long down=SystemClock.uptimeMillis();
        event(test,MotionEvent.ACTION_DOWN,down,area.centerX(),area.centerY());
        event(test,MotionEvent.ACTION_UP,down,area.centerX(),area.centerY());
    }
    private static void benchmark(Instrumentation test,PaintActivity app,StringBuilder report)throws Exception {
        for(int[] size:new int[][]{{1284,1782},{1800,2470}}) {
            DrawingBook book=new DrawingBook(new ToneDocument(size[0],size[1]));
            for(int p=0;p<4;p++) {
                if(p>0)book.addPage();
                ToneDocument doc=book.current();doc.begin();
                for(int y=20;y<size[1]-20;y++)doc.paintSpan(20,size[0]-20,y,(y/80+p*60)%256);
                doc.finish();
            }
            main(test,() -> call(app,"replaceBook",new Class<?>[]{DrawingBook.class},book));idle(test);
            long[] times=new long[8];
            for(int i=0;i<times.length;i++) {
                final int target=i%4,slot=i;
                main(test,() -> {
                    long start=System.nanoTime();
                    call(app,"changePage",new Class<?>[]{int.class},target);
                    times[slot]=(System.nanoTime()-start)/1000000;
                });idle(test);
            }
            java.util.Arrays.sort(times);
            report.append("Page turn CPU including selection, raster, submission and recovery snapshot ")
                    .append(size[0]).append('x').append(size[1]).append(": median=").append(times[4])
                    .append(" ms, max=").append(times[7]).append(" ms (not physical panel latency).\n");
        }
    }
    /** The open page menu's item named {@code name}. */
    static View pageMenuItem(PaintActivity app,String name)throws Exception {
        java.util.ArrayList<View> found=new java.util.ArrayList<>();
        ((View)get(app,"pagePanel")).findViewsWithText(found,name,View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        check(found.size()==1,"Page menu has one "+name+" item");
        return found.get(0);
    }
    private static void press(Instrumentation test,PaintActivity app,View button,DrawingBook book,int expected,SelectionFeedback feedback)throws Exception {
        RectF bounds=bounds(button);long down=SystemClock.uptimeMillis();int before=book.index(),submitted=feedback.submitted;
        int pageSubmissions=(Integer)get(get(app,"pad"),"pagePresentCount");
        android.widget.TextView counter=(android.widget.TextView)get(app,get(app,"popupPageNumber")!=null?"popupPageNumber":"pageNumber");
        // A page action also closes its panel, through the same fast path.
        Object panel=get(app,"pagePanel");
        boolean[] releasedBeforeCounter={false};
        android.text.TextWatcher watcher=new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                releasedBeforeCounter[0]=!((PageActionButton)button).press.pressed;
            }
            public void afterTextChanged(android.text.Editable text) {}
        };
        main(test,() -> counter.addTextChangedListener(watcher));
        try {
            check(PanelCoordinates.fullyVisible(button,new android.graphics.Rect(0,0,button.getWidth(),button.getHeight())),
                    "Page buttons must not be clipped, including density rounding");
            event(test,MotionEvent.ACTION_DOWN,down,bounds.centerX(),bounds.centerY());
            main(test,() -> {
                check((Boolean)get(get(button,"press"),"pressed"),"Press is visibly retained on pen-down");
                check(feedback.submitted>submitted,"Press uses fast display before loading");
                check(feedback.lastDisplayMode==(DirectEink.fastBinaryControls()?9:7),"Page arrow press uses device-specific fast mode");
                check(book.index()==before,"Page work waits until click");
            });
            event(test,MotionEvent.ACTION_UP,down,bounds.centerX(),bounds.centerY());idle(test);
            check(book.index()==expected,"Correct page after action");
            check(releasedBeforeCounter[0],"Press clears before page-counter redraw and disabled-arrow dimming");
            check((Integer)get(get(app,"pad"),"pagePresentCount")>pageSubmissions,"Page artwork submitted through fast display");
            SystemClock.sleep(250);test.waitForIdleSync();
            check(!(Boolean)get(get(button,"press"),"pressed"),"Press clears after completion");
            boolean closed=panel!=null&&get(app,"pagePanel")==null;
            check(feedback.submitted==submitted+(closed?3:2),"Exactly one press and one release, with no delayed repeat");
            if(!closed)check(feedback.lastDisplayMode==(DirectEink.fastBinaryControls()?9:7),"Page arrow release uses device-specific fast mode");
        } finally {main(test,() -> counter.removeTextChangedListener(watcher));}
    }
    private static void verifyRaster(StringBuilder report) {
        int width=257,height=193;
        java.util.ArrayList<ToneDocument.Layer> layers=new java.util.ArrayList<>();
        for(int n=0;n<8;n++) {
            byte[] tones=new byte[width*height],alpha=new byte[tones.length];
            for(int i=0;i<tones.length;i++){tones[i]=(byte)(i*17+n*61);alpha[i]=(byte)(i*7+n*31);}
            layers.add(new ToneDocument.Layer("Layer "+n,n%3!=1,tones,alpha));
        }
        ToneDocument doc=new ToneDocument(width,height,layers,0);
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);
        try {
            int[] expected=new int[width*height],actual=new int[expected.length];
            doc.render(expected,0,0,width,height);ViewportBitmap.compose(doc,bitmap,true);
            bitmap.getPixels(actual,0,width,0,0,width,height);
            check(java.util.Arrays.equals(expected,actual),"Native full-page raster matches Java for all shades, alpha and hidden layers");
            android.graphics.Bitmap same=ViewportBitmap.dithered(doc,width,height);
            same.getPixels(actual,0,width,0,0,width,height);same.recycle();
            check(java.util.Arrays.equals(expected,actual),"Dithered export at page size matches the screen");
            byte[] gray=new byte[64*48];java.util.Arrays.fill(gray,(byte)140);
            ToneDocument flat=new ToneDocument(64,48,gray);
            int[] dots=new int[40*30],shrunk=new int[dots.length];
            new ToneDocument(40,30,java.util.Arrays.copyOf(gray,dots.length)).render(dots,0,0,40,30);
            android.graphics.Bitmap scaled=ViewportBitmap.dithered(flat,40,30);
            check(scaled.getWidth()==40 && scaled.getHeight()==30,"Dithered export takes the chosen size");
            scaled.getPixels(shrunk,0,40,0,0,40,30);scaled.recycle();
            check(java.util.Arrays.equals(dots,shrunk),"Scaled export dithers its shade at the new resolution");
            for(int i=0;i<8;i++)doc.setLayerVisible(i,false);
            ViewportBitmap.compose(doc,bitmap,true);bitmap.getPixels(actual,0,width,0,0,width,height);
            for(int pixel:actual)check(pixel==android.graphics.Color.WHITE,"All hidden layers leave white paper");
        } finally {bitmap.recycle();}
        report.append("PASS: Native page raster exactly matches calibrated Java rendering across eight alpha/hidden layers, and dithered exports at page and scaled sizes.\n");
    }
    private static void event(Instrumentation test,int action,long down,float x,float y) {
        MotionEvent.PointerProperties properties=new MotionEvent.PointerProperties();properties.id=0;properties.toolType=MotionEvent.TOOL_TYPE_STYLUS;
        MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords();coords.x=x;coords.y=y;coords.pressure=.4f;
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,1,new MotionEvent.PointerProperties[]{properties},
                new MotionEvent.PointerCoords[]{coords},0,0,1,1,0,0,InputDevice.SOURCE_STYLUS,0);
        try{check(test.getUiAutomation().injectInputEvent(event,true),"Pen input injected");}finally{event.recycle();}
    }
    private static RectF bounds(View view){RectF rect=new RectF(0,0,view.getWidth(),view.getHeight());PanelCoordinates.fromView(view).mapRect(rect);return rect;}
    private static void idle(Instrumentation test){test.waitForIdleSync();SystemClock.sleep(180);}
    private static void screenshot(Instrumentation test,PaintActivity app,String name)throws Exception {
        android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(app.getCacheDir(),name))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
        }finally{bitmap.recycle();}
    }
    private interface Work {void run()throws Exception;}
    private static void main(Instrumentation test,Work work)throws Exception {
        Throwable[] error={null};test.runOnMainSync(() -> {try{work.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new Exception(error[0]);
    }
    private static Object get(Object owner,String name)throws Exception {Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static Object call(Object owner,String name,Class<?>[] types,Object... args)throws Exception {Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(owner,args);}
    private static Object call(Object owner,String name)throws Exception{return call(owner,name,new Class<?>[0]);}
    private static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
}
