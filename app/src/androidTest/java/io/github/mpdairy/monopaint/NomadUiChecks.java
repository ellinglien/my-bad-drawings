package io.github.mpdairy.monopaint;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Runs on ShapeUiChecks' disposable book and shares its verified restoration. */
final class NomadUiChecks {
    static void run(Instrumentation test,PaintActivity app,StringBuilder report)throws Exception {
        if(test instanceof WidthInstrumentation && ((WidthInstrumentation)test).rotationPromptOnly) {
            RotationPromptChecks.run(test,app,report);return;
        }
        if(!(Boolean)call(app,"canSimulate")) {report.append("SKIPPED Nomad simulation: this tablet cannot simulate a smaller one.\n");return;}
        SharedPreferences prefs=(SharedPreferences)get(app,"preferences");
        boolean original=prefs.getBoolean("nomad_mode",false);
        View frame=(View)get(app,"orientationFrame"),pad=(View)get(app,"pad");
        View menu=(View)get(app,"menuButton");int controlWidth=menu.getWidth();
        ToneDocument doc=(ToneDocument)get(pad,"document");byte[] before=doc.snapshot();
        AlertDialog[] dialog={null};
        try {
            main(test,() -> call(app,"setSimulating",new Class<?>[]{boolean.class},false));idle(test);
            // MonoPaint's settings dialog, once the simulation's toggle, was deleted (the refocus, 2026-10-24).
            main(test,() -> call(app,"setSimulating",new Class<?>[]{boolean.class},true));idle(test);
            check(prefs.getBoolean("nomad_mode",false),"Simulation persists");
            for(int turn=0;turn<4;turn++)for(boolean right:new boolean[]{false,true}) {
                final int quarter=turn;
                main(test,() -> {
                    prefs.edit().putBoolean("toolbox_right",right).apply();
                    call(app,"requestQuarter",new Class<?>[]{int.class},quarter);call(app,"applyToolboxSide");
                });idle(test);
                main(test,() -> {
                    RectF area=bounds(frame);
                    check(area.equals(new RectF(258,344,1662,2216)),"Exact centered physical viewport: "+area);
                    check(((android.graphics.drawable.ColorDrawable)((View)get(app,"previewFrame")).getBackground()).getColor()==android.graphics.Color.BLACK,"Simulation margins are black");
                    check(menu.getWidth()==controlWidth,"Physical control size unchanged");
                    check(area.contains(bounds(pad)),"Canvas inside preview");
                    layout(app,right);
                    check(get(pad,"direct")!=null,"Manta fast display is connected in centered viewport");
                    check(Arrays.equals(before,doc.snapshot()),"Toggles and rotations preserve document");
                });
                // A real screen-coordinate pen replay checks the new ancestor offset.
                long down=SystemClock.uptimeMillis();
                ShapeUiChecks.pen(test,pad,MotionEvent.ACTION_DOWN,down,150,180);
                ShapeUiChecks.pen(test,pad,MotionEvent.ACTION_MOVE,down,280,320);
                ShapeUiChecks.pen(test,pad,MotionEvent.ACTION_UP,down,280,320);idle(test);
                main(test,() -> {check(doc.canUndo(),"Offset stylus reaches canvas");
                    boolean ink=false;for(int y=248;y<=252;y++)for(int x=213;x<=217;x++)ink|=doc.opacity(x,y)>0;
                    check(ink,"Offset stylus paints at expected document coordinates");check(doc.undo(),"Stroke undo");
                    check(Arrays.equals(before,doc.snapshot()),"Undo restores test page");call(pad,"renderAll");pad.invalidate();});
                tapAt(test,120,120);idle(test);
                check(Arrays.equals(before,doc.snapshot())&&!doc.canUndo(),"Blank margin does not paint");
                main(test,() -> call(app,"fileMenu",new Class<?>[]{View.class},menu));idle(test);
                PopupWindow file=(PopupWindow)get(app,"filePopup");
                check(bounds(frame).contains(bounds(file.getContentView())),"File menu inside preview");
                main(test,file::dismiss);
                main(test,() -> call(app,"showToolSettings"));idle(test);
                PopupWindow tools=(PopupWindow)get(app,"toolPicker");
                check(bounds(frame).contains(bounds(tools.getContentView())),"Tool panel inside preview");
                main(test,tools::dismiss);
                if(!right)screenshot(test,app,"nomad-"+turn+".png");
                report.append("PASS: centered Nomad viewport, unchanged controls, pen, margins and panels; turn=").append(turn).append(" right=").append(right).append(".\n");
            }
            main(test,() -> call(app,"setSimulating",new Class<?>[]{boolean.class},false));idle(test);
            check(!prefs.getBoolean("nomad_mode",true)&&frame.getWidth()==1920&&frame.getHeight()==2560,"Off restores full screen");
            report.append("PASS: Nomad simulation can be turned off.\n");
            PageNavigationChecks.run(test,app,report);
            RotationPromptChecks.run(test,app,report);
        } finally {
            main(test,() -> {
                call(app,"closePagePanel");
                if(dialog[0]!=null)dialog[0].dismiss();
                PopupWindow tool=(PopupWindow)get(app,"toolPicker");if(tool!=null)tool.dismiss();
                PopupWindow file=(PopupWindow)get(app,"filePopup");if(file!=null)file.dismiss();
                call(app,"setSimulating",new Class<?>[]{boolean.class},original);
            });idle(test);
        }
    }
    /**
     * The drawing screen as laid out (the design pass, 2026-10-23), in the app's own turn: the page bar along the page's
     * top, the brush line along the foot under it, Zen Mode's button in the foot corner on the column's axis, the brush
     * line starting a clear gap past it, Menu level with the page previews.
     */
    static void layout(PaintActivity app,boolean right)throws Exception {
        View root=app.root;
        RectF page=Popups.bounds(root,app.pad),bar=Popups.bounds(root,app.inkWashBar.turner.view),line=Popups.bounds(root,app.inkWashBar.brushLine),
                column=Popups.bounds(root,app.inkWashBar.view),zen=Popups.bounds(root,(View)get(app,"zenButton"));
        check(bar.top<1 && Math.abs(bar.bottom-page.top)<2 && Math.abs(bar.left-page.left)<2 && Math.abs(bar.right-page.right)<2,"Page bar along the page's top: "+bar+" over "+page);
        // Its row's top is the line's top, the room under it centring the line on Zen Mode's icon (PaintActivity's linePlace).
        check(Math.abs(line.top-page.bottom)<2 && line.bottom<=root.getHeight()+1,"Brush line along the foot under the page: "+line);
        check(Math.abs(zen.bottom-root.getHeight())<2 && Math.abs(zen.centerX()-column.centerX())<2
                && zen.top>=column.bottom-app.inkWashBar.view.getPaddingBottom()-1,"Zen Mode's button in the foot corner on the column's axis: "+zen+" column "+column);
        // A clear gap after Zen Mode's button (the owner, 2026-10-23: "move the active tool info over a bit").
        float gap=app.dp(DrawLayout.LINE_GAP);
        check(right ? Math.abs(zen.left-line.right-gap)<2 : Math.abs(line.left-zen.right-gap)<2,"The brush line starts "+DrawLayout.LINE_GAP+" dp past Zen Mode's button: "+line+" "+zen);
        // Menu level with the page previews (the owner, 2026-10-23: "make the menu icon in the upper right in line with the page previews").
        RectF menu=Popups.bounds(root,(View)get(app.inkWashBar,"menu"));
        DrawLayout at=app.inkWashBar.layout();
        check(Math.abs(menu.centerY()-app.dp(DrawLayout.STRIP_EDGE+at.previewHeight()/2f))<2,"Menu centred on the page previews: "+menu+" under "+bar);
        // Layout v3 (the owner's mockup, 2026-10-26): under the tray the smudge tool and the eraser, a slot's row each; the
        // readout over the strip and the slider (the strip left since 2026-10-27); layout v4 (2026-10-26): under them the
        // Dropper and the Lasso, Refresh alone under them, the whole row (2026-10-28, a finger's target); the wet indicator and the sand timer, then Template Visibility
        // and Show Kinds, under the block (2026-10-28).
        View slider=(View)get(app.inkWashBar.swatches,"slider"),strip=(View)get(app.inkWashBar.swatches,"strip"),readout=(View)get(app.inkWashBar.swatches,"readout");
        RectF s=Popups.bounds(root,slider),g=Popups.bounds(root,strip),r=Popups.bounds(root,readout),
                wet=Popups.bounds(root,(View)get(app.inkWashBar,"wet")),drop=Popups.bounds(root,app.inkWashBar.swatches.dropper),
                lasso=Popups.bounds(root,(View)get(app.inkWashBar,"lasso")),kinds=Popups.bounds(root,app.inkWashBar.kinds),
                ghost=Popups.bounds(root,app.inkWashBar.ghost),shapes=Popups.bounds(root,app.inkWashBar.shapes),play=Popups.bounds(root,app.inkWashBar.play),
                sand=Popups.bounds(root,(View)get(app.inkWashBar,"sandTimer"));
        check(Math.abs(s.top-g.top)<2 && Math.abs(s.bottom-g.bottom)<2 && Math.abs(g.right-s.left)<2 && Math.abs(r.bottom-g.top)<2 && r.left>=column.left-1 && r.right<=column.right+1
                && Math.abs(r.centerX()-column.centerX())<3,"The readout over the strip and the slider: "+r+" over "+g+" "+s);
        check(Math.abs(lasso.top-drop.top)<2 && Math.abs(lasso.bottom-drop.bottom)<2
                && Math.abs(drop.right-lasso.left)<2 && Math.abs(ghost.left-drop.left)<2 && Math.abs(ghost.right-lasso.right)<2
                && ghost.height()>=drop.height()-1 && drop.top>=g.bottom-1,
                "The Dropper and the Lasso under the strip: "+drop+" "+lasso);
        // Shapes and Play under Show Kinds and the sand timer (the owner, 2026-10-30), the wet indicator under them.
        check(Math.abs(kinds.top-sand.top)<2 && Math.abs(kinds.right-sand.left)<2 && Math.abs(kinds.top-drop.bottom)<2
                && Math.abs(shapes.top-kinds.bottom)<2 && Math.abs(play.top-shapes.top)<2 && Math.abs(shapes.right-play.left)<2 && Math.abs(wet.top-shapes.bottom)<2
                && ghost.top>wet.bottom && column.contains(ghost),
                "Show Kinds and the sand timer, Shapes and Play, the wet indicator on its own line, then Refresh: "+kinds+" "+sand+" "+shapes+" "+play+" "+wet+" "+ghost);
        ToolButton[] tools=(ToolButton[])get(app.inkWashBar,"tools");
        RectF eraser=Popups.bounds(root,tools[InkWash.ERASER]),smudge=Popups.bounds(root,tools[InkWash.SMUDGE]),last=Popups.bounds(root,tools[DrawLayout.COMPACT_TRAYS-1]);
        check(smudge.top>=last.bottom-1 && Math.abs(smudge.width()-last.width())<2 && Math.abs(smudge.left-last.left)<2
                && Math.abs(eraser.top-smudge.bottom)<2 && Math.abs(eraser.width()-last.width())<2 && eraser.bottom<=r.top+1,
                "The smudge tool and the eraser under the tray, a slot's row each, over the readout: "+smudge+" "+eraser);
        // Every control in the column inside it: a clipped one can't use the fast display (2026-10-24).
        for (View v : new View[]{slider,strip,readout,app.inkWashBar.swatches.dropper}) {
            android.graphics.Rect seen=new android.graphics.Rect();
            // Its whole area seen, either way up (the app's turn may stand it on its side).
            check(v.getGlobalVisibleRect(seen) && seen.width()*seen.height()==v.getWidth()*v.getHeight(),"Not clipped: "+v+" "+seen);
        }
    }
    private static RectF bounds(View view) {
        RectF rect=new RectF(0,0,view.getWidth(),view.getHeight());PanelCoordinates.fromView(view).mapRect(rect);return rect;
    }
    private static void idle(Instrumentation test){test.waitForIdleSync();SystemClock.sleep(150);}
    private static void tap(Instrumentation test,View view)throws Exception {
        check(view!=null,"Control exists");RectF rect=bounds(view);tapAt(test,rect.centerX(),rect.centerY());
    }
    private static void tapAt(Instrumentation test,float x,float y) {
        long down=SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {
            MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try{check(test.getUiAutomation().injectInputEvent(event,true),"Tap injected");}finally{event.recycle();}
        }
    }
    private static View find(View view,String description) {
        if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            View found=find(((ViewGroup)view).getChildAt(i),description);if(found!=null)return found;
        }
        return null;
    }
    private static void screenshot(Instrumentation test,PaintActivity app,String name)throws Exception {
        Bitmap bitmap=test.getUiAutomation().takeScreenshot();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(app.getCacheDir(),name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
        }finally{bitmap.recycle();}
    }
    private interface Work {void run()throws Exception;}
    private static void main(Instrumentation test,Work work)throws Exception {
        Throwable[] error={null};test.runOnMainSync(() -> {try{work.run();}catch(Throwable e){error[0]=e;}});
        if(error[0]!=null)throw new Exception(error[0]);
    }
    private static Object get(Object owner,String name)throws Exception {Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static Object call(Object owner,String name,Class<?>[] types,Object... args)throws Exception {Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(owner,args);}
    private static Object call(Object owner,String name)throws Exception{return call(owner,name,new Class<?>[0]);}
    private static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
}
