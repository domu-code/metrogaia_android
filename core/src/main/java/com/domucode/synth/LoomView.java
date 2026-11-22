package com.domucode.synth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Locale;

/** Adaptive LOOM touchscreen editor.
 * MOD touch = desktop Ctrl-click equivalent; LEARN touch = MIDI learn.
 * Long press = contextual actions; FINE = high precision parameter editing.
 */
public final class LoomView extends View {
    public interface Host {
        void save();
        void load();
        void next();
        void previous();
        void select(int index);
        int patch();
        void loadWav();
        void loopsChanged();
        String status();
    }
    private static final int NORMAL=0,MOD=1,LEARN=2,FINE=3,LOCK=4;
    private static final String[] MODE_LABELS={"EDIT","MOD","LEARN","FINE","LOCK"};
    private static final String[] PAGE_NAMES={"MIX","ENGINE","MOD","FILTER","FX","SEQ","LOOPS","XY","PATCH","MIDI"};
    private static final String[] NOTE_NAMES={"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};
    private static final int[] WHITE={0,2,4,5,7,9,11};
    private static final boolean[] HAS_BLACK={true,true,false,true,true,true,false};
    private static final int BG=Color.rgb(13,17,21),PANEL=Color.rgb(27,33,39),
        BORDER=Color.rgb(72,87,94),TEXT=Color.rgb(224,234,231),
        MUTED=Color.rgb(133,151,154),ACCENT=Color.rgb(254,176,72),
        CYAN=Color.rgb(85,215,207),WARN=Color.rgb(243,123,112);
    private static final int KIND_BUTTON=0,KIND_PARAM=1,KIND_XY=2,KIND_KEY=3,KIND_MORPH=4;
    private static final class Zone {
        final RectF box;
        final int kind,param;
        final Runnable click;
        Zone(RectF rect,int k,int p,Runnable r){box=rect;kind=k;param=p;click=r;}
    }
    private final LoomEngine engine;
    private final AndroidMidi midi;
    private final Host host;
    private final PixelFont font=new PixelFont();
    private final Paint paint=new Paint();
    private final ArrayList<Zone> zones=new ArrayList<>();
    private final SparseIntArray activeKeys=new SparseIntArray();
    private final float density;
    private int mode=NORMAL,page=0,enginePage=0,selectedDest=-1,selectedRoute=-1;
    private int contextParam=-1;
    private boolean showModChooser=false,showContext=false;
    private float scroll=0,contentHeight,bodyTop,bodyBottom,keyboardTop,keyboardBottom,whiteWidth;
    private int keyboardWhiteCount;
    private float dragStartX,dragStartY,lastY,dragStartValue;
    private boolean moved;
    private Zone dragging;
    private long downTime;
    private String info="TOUCH A CONTROL TO EDIT";
    private float itemX,itemY,itemW,itemGap;
    private int cols;
    private final float minTouch;
    private float headerHeight;

    public LoomView(Context context,LoomEngine synth,AndroidMidi io,Host callbacks){
        super(context);engine=synth;midi=io;host=callbacks;
        density=context.getResources().getDisplayMetrics().density;
        minTouch=dp(44);
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        setFocusable(true);setFocusableInTouchMode(true);
    }
    private float dp(float x){return x*density;}
    private void rect(Canvas canvas,int color,float x,float y,float w,float h){
        paint.setColor(color);paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(x,y,x+w,y+h,paint);
    }
    private void line(Canvas canvas,int color,float x,float y,float w,float h){
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));
        canvas.drawRect(x,y,x+w,y+h,paint);paint.setStyle(Paint.Style.FILL);
    }
    private void label(Canvas c,String str,float x,float y,float size,int color){
        font.draw(c,str,x,y,dp(size),color);
    }
    private String fit(String s,float width,float size){
        int n=(int)(width/Math.max(1,dp(size)*6));
        if(s.length()<=n)return s;
        if(n<=1)return "";
        return s.substring(0,n-1)+">";
    }
    private void center(Canvas c,String str,float x,float y,float w,float h,float fontSize,int color){
        float px=dp(fontSize);
        String s=fit(str,w-dp(6),fontSize);
        font.draw(c,s,x+(w-font.width(s,px))*.5f,y+(h-5*px)*.5f,px,color);
    }
    private void add(float x,float y,float w,float h,int kind,int param,Runnable task){
        if(y+h<bodyTop||y>bodyBottom)return;
        zones.add(new Zone(new RectF(x,y,x+w,y+h),kind,param,task));
    }
    private void button(Canvas c,String str,float x,float y,float w,float h,boolean selected,Runnable task){
        if(y+h<bodyTop||y>bodyBottom)return;
        rect(c,selected?ACCENT:PANEL,x,y,w,h);
        line(c,selected?ACCENT:BORDER,x,y,w,h);
        center(c,str,x,y,w,h,w<dp(68)?1.45f:1.75f,selected?BG:TEXT);
        add(x,y,w,h,KIND_BUTTON,-1,task);
    }
    private void param(Canvas c,int id,float x,float y,float w,float h){
        if(id<0||id>=LoomEngine.NUM||y+h<bodyTop||y>bodyBottom)return;
        float value=engine.getParam(id);
        boolean mapped=false;
        for(int i=0;i<LoomEngine.ROUTES;i++)
            if(engine.routeSource[i]>=0&&engine.routeDest[i]==id)mapped=true;
        int stroke=mapped?CYAN:BORDER;
        if(engine.noteLockMode&&engine.noteLockTarget>=0&&engine.hasLock(engine.noteLockTarget,id))
            stroke=WARN;
        rect(c,PANEL,x,y,w,h);line(c,stroke,x,y,w,h);
        label(c,fit(LoomEngine.PARAM_NAMES[id],w-dp(68),1.6f),x+dp(9),y+dp(8),1.6f,TEXT);
        String val=String.format(Locale.US,"%03d",Math.round(value*100));
        label(c,val,x+w-dp(49),y+dp(8),1.6f,CYAN);
        float trackLeft=x+dp(9),trackWidth=w-dp(18),trackY=y+h-dp(17);
        rect(c,BORDER,trackLeft,trackY,trackWidth,dp(6));
        rect(c,ACCENT,trackLeft,trackY,trackWidth*value,dp(6));
        add(x,y,w,h,KIND_PARAM,id,null);
    }
    private void title(Canvas c,String s,float x,float y){
        label(c,s,x,y,2,ACCENT);
    }
    private float nextRow(int count,float y,float cellH){
        return y+(float)Math.ceil(count/(float)cols)*cellH;
    }
    private void paramGrid(Canvas c,int[] ids,float y,float h){
        for(int i=0;i<ids.length;i++){
            int col=i%cols,row=i/cols;
            param(c,ids[i],itemX+col*(itemW+itemGap),y+row*(h+dp(7)),itemW,h);
        }
    }
    private int[] range(int start,int n){
        int[] arr=new int[n];for(int i=0;i<n;i++)arr[i]=start+i;return arr;
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        if(w<10||h<10)return;
        zones.clear();
        rect(c,BG,0,0,w,h);
        boolean tablet=(w/density)>=670;
        cols=tablet?2:1;
        itemGap=dp(8);itemX=dp(9);
        itemW=(w-dp(18)-(cols-1)*itemGap)/cols;
        headerHeight=tablet?dp(58):dp(51);
        float modesH=dp(43);
        float navH=tablet?dp(45):dp(86);
        bodyTop=headerHeight+modesH+navH;
        float keyboardH=Math.min(dp(158),h*.27f);
        keyboardTop=h-keyboardH;keyboardBottom=h;
        bodyBottom=keyboardTop-dp(3);
        label(c,"LOOM / MOBILE",dp(10),dp(9),tablet?2.8f:2.3f,ACCENT);
        label(c,fit(info,w*.72f,1.45f),dp(10),headerHeight-dp(18),1.45f,MUTED);
        float meterW=dp(65);
        rect(c,BORDER,w-meterW-dp(9),dp(13),meterW,dp(8));
        rect(c,CYAN,w-meterW-dp(9),dp(13),meterW*engine.meterMobile(),dp(8));
        label(c,String.format(Locale.US,"%02dV",engine.voiceCount()),w-dp(67),dp(30),1.5f,CYAN);
        float gap=dp(3),modeW=(w-gap*6)/5f;
        for(int i=0;i<5;i++){
            int index=i;float x=gap+i*(modeW+gap);
            float y=headerHeight;
            rect(c,mode==i?ACCENT:PANEL,x,y,modeW,modesH-dp(3));
            center(c,MODE_LABELS[i],x,y,modeW,modesH-dp(3),modeW<dp(65)?1.35f:1.65f,mode==i?BG:TEXT);
            zones.add(new Zone(new RectF(x,y,x+modeW,y+modesH),KIND_BUTTON,-1,
                ()->{
                    mode=mode==index?NORMAL:index;
                    engine.noteLockMode=(mode==LOCK);
                    showModChooser=showContext=false;
                    info=mode==MOD?"TAP DESTINATION THEN MOD SOURCE":
                        mode==LEARN?"TAP CONTROL THEN SEND MIDI CC":
                        mode==FINE?"DRAG FOR FINE ADJUST":
                        mode==LOCK?"PLAY NOTE THEN ADJUST PARAMETER":"EDIT MODE";
                }));
        }
        float navY=headerHeight+modesH;
        int pageCols=tablet?10:5;
        float pageW=w/pageCols;
        for(int i=0;i<PAGE_NAMES.length;i++){
            final int n=i;
            int col=i%pageCols,row=i/pageCols;
            float x=col*pageW,y=navY+row*dp(42);
            rect(c,page==i?CYAN:PANEL,x+dp(1),y+dp(1),pageW-dp(2),dp(40));
            center(c,PAGE_NAMES[i],x+dp(1),y,pageW-dp(2),dp(42),1.4f,page==i?BG:TEXT);
            zones.add(new Zone(new RectF(x,y,x+pageW,y+dp(42)),KIND_BUTTON,-1,
                ()->{page=n;scroll=0;showContext=showModChooser=false;}));
        }
        c.save();
        c.clipRect(0,bodyTop,w,bodyBottom);
        float y=bodyTop+dp(10)-scroll;
        switch(page){
            case 0:y=drawMix(c,y);break;
            case 1:y=drawEngine(c,y);break;
            case 2:y=drawMod(c,y);break;
            case 3:y=drawFilter(c,y);break;
            case 4:y=drawFx(c,y);break;
            case 5:y=drawSeq(c,y);break;
            case 6:y=drawLooper(c,y);break;
            case 7:y=drawXY(c,y);break;
            case 8:y=drawPatches(c,y);break;
            case 9:y=drawMidi(c,y);break;
        }
        contentHeight=Math.max(0,y+scroll-bodyTop+dp(12));
        c.restore();
        drawKeyboard(c,w);
        if(showModChooser)drawSourcePicker(c,w);
        if(showContext)drawContext(c,w);
        postInvalidateDelayed(70);
    }
    private float drawMix(Canvas c,float y){
        title(c,"EIGHT ENGINE MIXER",itemX,y);y+=dp(29);
        paramGrid(c,range(0,8),y,dp(58));
        y=nextRow(8,y,dp(65))+dp(12);
        title(c,"MASTER OUTPUT",itemX,y);y+=dp(28);
        paramGrid(c,new int[]{LoomEngine.P_MASTER,LoomEngine.P_FILTER,LoomEngine.P_REVERB,LoomEngine.P_DELAY},y,dp(58));
        y=nextRow(4,y,dp(65))+dp(10);
        label(c,"TOUCH ENGINE PAGE FOR MODELS",itemX,y,1.7f,MUTED);y+=dp(32);
        return y;
    }
    private float drawEngine(Canvas c,float y){
        title(c,"SOUND SOURCES",itemX,y);y+=dp(26);
        float gap=dp(5);
        int ncol=4;
        float bw=(getWidth()-dp(18)-(ncol-1)*gap)/ncol;
        for(int i=0;i<8;i++){
            final int index=i;
            int col=i%4,row=i/4;
            button(c,fit(LoomEngine.ENGINE_NAMES[i],bw-dp(5),1.4f),
                itemX+col*(bw+gap),y+row*dp(49),bw,dp(44),enginePage==i,
                ()->{enginePage=index;info=LoomEngine.ENGINE_NAMES[index];});
        }
        y+=dp(106);
        title(c,LoomEngine.ENGINE_NAMES[enginePage]+" EDITOR",itemX,y);y+=dp(27);
        int e=enginePage;
        paramGrid(c,new int[]{e,8+e,16+e,24+e,32+e},y,dp(60));
        y=nextRow(5,y,dp(67))+dp(10);
        String desc=e==0?"24 PLAITS MODEL VARIANTS":
                    e==1?"48 BRAIDS MODEL GROUPS":
                    e==2?"DMG DUTY / WAVES / NOISE":
                    e==3?"WAV SAMPLE + GRAIN REPEAT":
                    e==4?"FORMANT VOICE SYNTHESIS":
                    e==5?"KARPLUS RING RESONATOR":
                    e==6?"EIGHT-MODE HANDPAN":
                         "BREATH / REED WAVEGUIDE";
        label(c,fit(desc,getWidth()-dp(20),1.7f),itemX,y,1.7f,CYAN);
        y+=dp(35);
        if(e==3){
            button(c,"IMPORT WAV",itemX,y,getWidth()-dp(18),dp(52),true,host::loadWav);y+=dp(59);
            label(c,fit("SAMPLE "+engine.sampleName,getWidth()-dp(18),1.6f),itemX,y,1.6f,TEXT);y+=dp(34);
            paramGrid(c,new int[]{LoomEngine.P_SAMPLESTART,LoomEngine.P_GRAIN},y,dp(58));
            y=nextRow(2,y,dp(65));
        }else if(e==5||e==6||e==7){
            int p=e==5?LoomEngine.P_RINGDAMP:e==6?LoomEngine.P_MATERIAL:LoomEngine.P_BREATH;
            paramGrid(c,new int[]{p},y,dp(60));y=nextRow(1,y,dp(67));
        }
        return y+dp(18);
    }
    private float drawMod(Canvas c,float y){
        title(c,"TOUCH MOD ASSIGNMENTS",itemX,y);y+=dp(28);
        label(c,"MOD MODE + DESTINATION = CTRL CLICK",itemX,y,1.55f,CYAN);y+=dp(30);
        paramGrid(c,new int[]{LoomEngine.P_LFO1,LoomEngine.P_LFODEPTH1,
            LoomEngine.P_LFO2,LoomEngine.P_LFODEPTH2},y,dp(58));
        y=nextRow(4,y,dp(65))+dp(13);
        title(c,"MOD ROUTES / DEPTH",itemX,y);y+=dp(28);
        int count=0;
        for(int i=0;i<LoomEngine.ROUTES;i++){
            if(engine.routeSource[i]<0)continue;
            int index=i;float rowY=y;count++;
            String title=LoomEngine.MOD_NAMES[Math.min(engine.routeSource[i],LoomEngine.MOD_NAMES.length-1)]+" > "+
                LoomEngine.PARAM_NAMES[Math.min(engine.routeDest[i],LoomEngine.NUM-1)];
            button(c,fit(title,getWidth()-dp(95),1.35f),itemX,rowY,
                getWidth()-dp(96),dp(43),selectedRoute==i,()->{selectedRoute=index;});
            button(c,"X",getWidth()-dp(79),rowY,dp(65),dp(43),false,()->{
                engine.deleteMod(index);selectedRoute=-1;
            });y+=dp(49);
        }
        if(count==0){label(c,"NO ROUTES / SELECT MOD THEN CONTROL",itemX,y,1.6f,MUTED);y+=dp(31);}
        if(selectedRoute>=0&&engine.routeSource[selectedRoute]>=0){
            title(c,"SIGNED MODULATION DEPTH",itemX,y);y+=dp(27);
            float x=itemX,w=getWidth()-dp(18),h=dp(57);
            rect(c,PANEL,x,y,w,h);
            float val=(engine.routeDepth[selectedRoute]+1)*.5f;
            rect(c,BORDER,x+dp(12),y+dp(36),w-dp(24),dp(9));
            rect(c,CYAN,x+dp(12),y+dp(36),(w-dp(24))*val,dp(9));
            String depth=String.format(Locale.US,"%+.2f",engine.routeDepth[selectedRoute]);
            label(c,"DEPTH "+depth,x+dp(11),y+dp(9),1.7f,TEXT);
            int routeIndex=selectedRoute;
            zones.add(new Zone(new RectF(x,y,x+w,y+h),KIND_BUTTON,-1,
                ()->{engine.routeDepth[routeIndex]=Math.max(-1,Math.min(1,engine.routeDepth[routeIndex]+.1f));}));
            y+=dp(70);
            button(c,"DEPTH -",itemX,y,(w-dp(7))*.5f,dp(48),false,()->engine.routeDepth[routeIndex]=Math.max(-1,engine.routeDepth[routeIndex]-.1f));
            button(c,"DEPTH +",itemX+(w+dp(7))*.5f,y,(w-dp(7))*.5f,dp(48),false,
                ()->engine.routeDepth[routeIndex]=Math.min(1,engine.routeDepth[routeIndex]+.1f));
            y+=dp(59);
        }
        return y+dp(10);
    }
    private float drawFilter(Canvas c,float y){
        title(c,"FILTER AND ENVELOPES",itemX,y);y+=dp(27);
        int[] p={LoomEngine.P_FILTER,LoomEngine.P_RESO,LoomEngine.P_ATTACK,
            LoomEngine.P_DECAY,LoomEngine.P_SUSTAIN,LoomEngine.P_RELEASE};
        paramGrid(c,p,y,dp(62));y=nextRow(p.length,y,dp(70))+dp(25);
        label(c,"MOD / LEARN / FINE WORK ON ALL KNOBS",itemX,y,1.5f,MUTED);y+=dp(34);
        return y;
    }
    private float drawFx(Canvas c,float y){
        title(c,"REORDERABLE EFFECTS",itemX,y);y+=dp(26);
        float w=getWidth()-dp(18);
        for(int i=0;i<5;i++){
            final int orderIndex=i,fx=engine.fxOrder[i];
            float onW=w*.22f,downW=w*.16f,upW=w*.16f,captionW=w-onW-downW-upW-dp(15);
            button(c,engine.fxEnabled[fx]?"ON":"OFF",itemX,y,onW,dp(44),engine.fxEnabled[fx],
                ()->engine.fxEnabled[fx]=!engine.fxEnabled[fx]);
            button(c,LoomEngine.FX_NAMES[fx],itemX+onW+dp(5),y,captionW,dp(44),false,()->{});
            button(c,"UP",itemX+onW+captionW+dp(10),y,upW,dp(44),false,()->{
                if(orderIndex>0){int t=engine.fxOrder[orderIndex-1];engine.fxOrder[orderIndex-1]=fx;engine.fxOrder[orderIndex]=t;}
            });
            button(c,"DN",itemX+onW+captionW+upW+dp(15),y,downW,dp(44),false,()->{
                if(orderIndex<4){int t=engine.fxOrder[orderIndex+1];engine.fxOrder[orderIndex+1]=fx;engine.fxOrder[orderIndex]=t;}
            });
            y+=dp(52);
        }
        y+=dp(10);
        paramGrid(c,new int[]{LoomEngine.P_DRIVE,LoomEngine.P_CRUSH,
            LoomEngine.P_CHORUS,LoomEngine.P_DELAY,LoomEngine.P_REVERB},y,dp(57));
        y=nextRow(5,y,dp(65))+dp(12);
        return y;
    }
    private float drawSeq(Canvas c,float y){
        title(c,"ARPEGGIATOR AND STEP SEQUENCER",itemX,y);y+=dp(29);
        float w=getWidth()-dp(18),half=(w-dp(6))*.5f;
        button(c,engine.arpEnabled?"ARP ON":"ARP OFF",itemX,y,half,dp(50),engine.arpEnabled,
            ()->engine.arpEnabled=!engine.arpEnabled);
        button(c,engine.seqEnabled?"SEQ ON":"SEQ OFF",itemX+half+dp(6),y,half,dp(50),engine.seqEnabled,
            ()->engine.seqEnabled=!engine.seqEnabled);y+=dp(60);
        paramGrid(c,new int[]{LoomEngine.P_TEMPO,LoomEngine.P_ARPGATE},y,dp(57));
        y=nextRow(2,y,dp(64))+dp(13);
        title(c,"16 STEPS / ENABLE OR DISABLE",itemX,y);y+=dp(28);
        int ncol=(getWidth()/density>=670)?8:4;
        float gap=dp(5),bw=(w-(ncol-1)*gap)/ncol;
        for(int i=0;i<16;i++){
            int j=i;
            int col=i%ncol,row=i/ncol;
            button(c,String.format(Locale.US,"%02d",i+1),itemX+col*(bw+gap),y+row*dp(53),
                bw,dp(48),engine.stepEnabled[i],()->engine.stepEnabled[j]=!engine.stepEnabled[j]);
        }
        y+=((16+ncol-1)/ncol)*dp(53)+dp(13);
        title(c,"SEQUENCED NOTE ROOT",itemX,y);y+=dp(31);
        button(c,"NOTES -",itemX,y,half,dp(49),false,()->{
            for(int i=0;i<16;i++)engine.stepNotes[i]=Math.max(0,engine.stepNotes[i]-1);
        });
        button(c,"NOTES +",itemX+half+dp(6),y,half,dp(49),false,()->{
            for(int i=0;i<16;i++)engine.stepNotes[i]=Math.min(127,engine.stepNotes[i]+1);
        });y+=dp(65);
        return y;
    }
    private float drawLooper(Canvas c,float y){
        title(c,"8 BANKS / 64 MIDI LOOP SLOTS",itemX,y);y+=dp(29);
        float w=getWidth()-dp(18),gap=dp(5),cw=(w-gap*3)/4;
        button(c,"BANK -",itemX,y,cw,dp(48),false,()->engine.activeBank=(engine.activeBank+7)%8);
        button(c,"B "+(engine.activeBank+1),itemX+cw+gap,y,cw,dp(48),true,()->{});
        button(c,"BANK +",itemX+(cw+gap)*2,y,cw,dp(48),false,()->engine.activeBank=(engine.activeBank+1)%8);
        button(c,"STORE",itemX+(cw+gap)*3,y,cw,dp(48),false,host::loopsChanged);
        y+=dp(63);
        for(int i=0;i<8;i++){
            int index=i,col=i%4,row=i/4;
            LoomEngine.LoopSlot slot=engine.loops[engine.activeBank][i];
            String caption=(i+1)+(slot.recording?" REC":slot.playing?" PLAY":slot.events.isEmpty()?" ---":" MIDI");
            button(c,caption,itemX+col*(cw+gap),y+row*dp(58),cw,dp(52),engine.activeSlot==i,
                ()->engine.activeSlot=index);
        }
        y+=dp(128);
        LoomEngine.LoopSlot slot=engine.loops[engine.activeBank][engine.activeSlot];
        title(c,"SLOT "+(engine.activeSlot+1)+" / "+slot.events.size()+" EVENTS",itemX,y);y+=dp(29);
        button(c,slot.recording?"STOP REC":"RECORD",itemX,y,cw,dp(52),slot.recording,()->{
            engine.toggleRecord();host.loopsChanged();
        });
        button(c,slot.playing?"STOP":"PLAY",itemX+(cw+gap),y,cw,dp(52),slot.playing,engine::togglePlay);
        button(c,slot.overdub?"OD ON":"OVERDUB",itemX+(cw+gap)*2,y,cw,dp(52),slot.overdub,()->{
            engine.toggleOverdub();host.loopsChanged();
        });
        button(c,"CLEAR",itemX+(cw+gap)*3,y,cw,dp(52),false,()->{
            engine.clearLoop();host.loopsChanged();
        });y+=dp(71);
        label(c,"RECORD MIDI NOTE AND CC EVENTS",itemX,y,1.6f,MUTED);y+=dp(30);
        label(c,"FREE LENGTH / AUTO REPEAT",itemX,y,1.6f,MUTED);y+=dp(30);
        return y;
    }
    private float drawXY(Canvas c,float y){
        title(c,"XY TOUCH MODULATION",itemX,y);y+=dp(29);
        float w=getWidth()-dp(18),h=Math.max(dp(170),Math.min(dp(345),bodyBottom-y-dp(30)));
        rect(c,PANEL,itemX,y,w,h);
        line(c,CYAN,itemX,y,w,h);
        float cx=itemX+w*engine.xyX,cy=y+h*(1-engine.xyY);
        paint.setColor(ACCENT);paint.setStrokeWidth(dp(2));
        c.drawLine(cx,y,cx,y+h,paint);c.drawLine(itemX,cy,itemX+w,cy,paint);
        c.drawCircle(cx,cy,dp(10),paint);
        label(c,"X / Y MOD SOURCES",itemX+dp(10),y+dp(10),1.6f,TEXT);
        zones.add(new Zone(new RectF(itemX,y,itemX+w,y+h),KIND_XY,-1,null));
        y+=h+dp(20);
        paramGrid(c,new int[]{LoomEngine.P_XYDEPTH},y,dp(60));y=nextRow(1,y,dp(65))+dp(5);
        label(c,"MOD MODE: ASSIGN XY X OR XY Y",itemX,y,1.6f,MUTED);y+=dp(30);
        return y;
    }
    private float drawPatches(Canvas c,float y){
        title(c,"LOCAL PATCH MEMORY / 8 SLOTS",itemX,y);y+=dp(29);
        float w=getWidth()-dp(18),gap=dp(6),bw=(w-gap*3)/4;
        for(int i=0;i<8;i++){
            final int number=i;
            button(c,"PATCH "+(i+1),itemX+(i%4)*(bw+gap),y+(i/4)*dp(58),
                bw,dp(52),host.patch()==i,()->host.select(number));
        }
        y+=dp(128);
        float half=(w-dp(7))*.5f;
        button(c,"LOAD",itemX,y,half,dp(56),false,host::load);
        button(c,"SAVE",itemX+half+dp(7),y,half,dp(56),true,host::save);
        y+=dp(75);
        title(c,"LOOM STATES / FOUR SNAPSHOTS",itemX,y);y+=dp(30);
        float stateW=(w-dp(18))/4f;
        for(int i=0;i<4;i++){
            final int index=i;
            button(c,"STATE "+(i+1),itemX+i*(stateW+dp(6)),y,stateW,dp(48),
                engine.activeState==i,()->{
                    engine.activeState=index;
                    info=engine.stateCaptured[index]?"STATE "+(index+1)+" READY":"STATE "+(index+1)+" EMPTY";
                });
        }
        y+=dp(59);
        button(c,"CAPTURE",itemX,y,half,dp(53),false,()->{
            engine.captureState(engine.activeState);info="CAPTURED STATE "+(engine.activeState+1);
        });
        button(c,"RECALL",itemX+half+dp(7),y,half,dp(53),false,()->{
            engine.recallState(engine.activeState);info="RECALLED STATE "+(engine.activeState+1);
        });
        y+=dp(66);
        button(c,"A "+(engine.stateA+1),itemX,y,half,dp(48),false,()->engine.stateA=(engine.stateA+1)%4);
        button(c,"B "+(engine.stateB+1),itemX+half+dp(7),y,half,dp(48),false,
            ()->engine.stateB=(engine.stateB+1)%4);
        y+=dp(60);
        rect(c,PANEL,itemX,y,w,dp(56));
        label(c,String.format(Locale.US,"STATE MORPH %03d",Math.round(engine.stateMorph*100)),
            itemX+dp(10),y+dp(8),1.7f,CYAN);
        rect(c,BORDER,itemX+dp(12),y+dp(38),w-dp(24),dp(7));
        rect(c,ACCENT,itemX+dp(12),y+dp(38),(w-dp(24))*engine.stateMorph,dp(7));
        zones.add(new Zone(new RectF(itemX,y,itemX+w,y+dp(56)),KIND_MORPH,-1,null));
        y+=dp(75);
        title(c,"PATCH INCLUDES",itemX,y);y+=dp(30);
        String[] details={"8 ENGINES AND PARAMETERS","MOD MATRIX AND MIDI CC MAPS",
            "FX STATES AND SIGNAL ORDER","ARP / STEPPER SETTINGS",
            "LOOP BANKS STORED SEPARATELY"};
        for(String str:details){label(c,str,itemX,y,1.6f,MUTED);y+=dp(29);}
        return y+dp(8);
    }
    private float drawMidi(Canvas c,float y){
        title(c,"MIDI CONTROLLERS / TOUCH",itemX,y);y+=dp(30);
        float w=getWidth()-dp(18),half=(w-dp(7))*.5f;
        button(c,"NEXT PORT",itemX,y,half,dp(51),false,midi::next);
        button(c,"SCAN",itemX+half+dp(7),y,half,dp(51),false,midi::refresh);
        y+=dp(62);
        label(c,fit(midi.label(),w,1.5f),itemX,y,1.5f,CYAN);y+=dp(42);
        button(c,"MIDI LEARN MODE",itemX,y,w,dp(52),mode==LEARN,()->{
            mode=LEARN;info="TAP CONTROL / MOVE MIDI CC";
        });y+=dp(64);
        button(c,"ALL NOTES OFF",itemX,y,w,dp(52),false,engine::allNotesOff);y+=dp(64);
        title(c,"LEARNED CONTROLLERS",itemX,y);y+=dp(28);
        int n=0;
        for(int cc=0;cc<128;cc++){
            int mapped=engine.ccMappings[cc];
            if(mapped<0||mapped>=LoomEngine.NUM)continue;
            String text="CC "+cc+" > "+LoomEngine.PARAM_NAMES[mapped];
            label(c,fit(text,w,1.6f),itemX,y,1.6f,TEXT);
            y+=dp(30);n++;
        }
        if(n==0){label(c,"NO LEARNED CC MAPPINGS",itemX,y,1.6f,MUTED);y+=dp(32);}
        title(c,"TOUCH MODIFIERS",itemX,y);y+=dp(30);
        String[] instructions={
            "MOD / DEST / SOURCE: CTRL CLICK",
            "LEARN / CONTROL / MIDI CC",
            "FINE / DRAG: PRECISE EDIT",
            "LOCK / PLAY NOTE / EDIT",
            "LONG PRESS: CONTEXT MENU",
            "PANIC / ALL NOTES OFF"};
        for(String s:instructions){label(c,fit(s,w,1.55f),itemX,y,1.55f,TEXT);y+=dp(30);}
        return y;
    }
    private int noteForWhite(int k){
        return (engine.activeOctave+1)*12+WHITE[k%7]+(k/7)*12;
    }
    private boolean held(int note){
        for(int i=0;i<activeKeys.size();i++)if(activeKeys.valueAt(i)==note)return true;
        return false;
    }
    private void drawKeyboard(Canvas c,float w){
        keyboardWhiteCount=(w/density>=670)?14:7;
        whiteWidth=w/keyboardWhiteCount;
        for(int i=0;i<keyboardWhiteCount;i++){
            int midi=noteForWhite(i);
            int color=held(midi)?ACCENT:TEXT;
            rect(c,color,i*whiteWidth+dp(1),keyboardTop+dp(3),
                whiteWidth-dp(2),keyboardBottom-keyboardTop-dp(6));
            if(i%7==0){
                label(c,"C"+(midi/12-1),i*whiteWidth+dp(5),keyboardBottom-dp(21),1.65f,BG);
            }
        }
        for(int i=0;i<keyboardWhiteCount;i++)if(HAS_BLACK[i%7]){
            int midi=noteForWhite(i)+1;
            float left=(i+1)*whiteWidth-whiteWidth*.3f;
            rect(c,held(midi)?ACCENT:BG,left,keyboardTop+dp(3),whiteWidth*.6f,
                (keyboardBottom-keyboardTop)*.61f);
        }
        line(c,BORDER,0,keyboardTop,w,keyboardBottom-keyboardTop);
    }
    private int keyAt(float x,float y){
        if(y<keyboardTop||y>keyboardBottom)return -1;
        for(int i=0;i<keyboardWhiteCount;i++)if(HAS_BLACK[i%7]){
            float left=(i+1)*whiteWidth-whiteWidth*.3f;
            if(x>=left&&x<left+whiteWidth*.6f&&y<keyboardTop+(keyboardBottom-keyboardTop)*.61f)
                return Math.min(127,noteForWhite(i)+1);
        }
        int k=(int)(x/whiteWidth);
        if(k<0||k>=keyboardWhiteCount)return -1;
        return Math.min(127,noteForWhite(k));
    }
    private Zone zoneAt(float x,float y){
        for(int i=zones.size()-1;i>=0;i--){
            Zone z=zones.get(i);
            if(z.box.contains(x,y))return z;
        }
        return null;
    }
    private void handleKeyboard(int pointer,float x,float y,boolean up){
        int old=activeKeys.get(pointer,-1),next=up?-1:keyAt(x,y);
        if(old==next)return;
        if(old>=0)engine.noteOff(old);
        activeKeys.delete(pointer);
        if(next>=0){activeKeys.put(pointer,next);engine.noteOn(next,110);}
    }
    public void releaseKeys(){
        for(int i=0;i<activeKeys.size();i++)engine.noteOff(activeKeys.valueAt(i));
        activeKeys.clear();
    }
    private void showAction(int id){
        contextParam=id;showContext=true;showModChooser=false;
        info=LoomEngine.PARAM_NAMES[id];
    }
    private void selectParam(int id){
        if(mode==MOD){
            selectedDest=id;showModChooser=true;showContext=false;
            info="CHOOSE SOURCE FOR "+LoomEngine.PARAM_NAMES[id];
        }else if(mode==LEARN){
            engine.armMidiLearn(id);
            info="LEARN ARMED / MOVE MIDI CC";
        }else if(mode==LOCK){
            if(engine.noteLockTarget<0)info="PLAY A NOTE THEN EDIT";
            else info="NOTE "+engine.noteLockTarget+" LOCK EDIT";
        }else info=LoomEngine.PARAM_NAMES[id];
    }
    private void drawSourcePicker(Canvas c,float w){
        float pad=dp(9);
        float overlayH=Math.min(bodyBottom-bodyTop,dp(282));
        float y=bodyBottom-overlayH;
        rect(c,BG,0,y,w,overlayH);
        line(c,ACCENT,pad,y+dp(4),w-pad*2,overlayH-dp(7));
        label(c,"SOURCE FOR "+fit(LoomEngine.PARAM_NAMES[selectedDest],w-dp(150),1.5f),
            pad+dp(7),y+dp(14),1.5f,CYAN);
        float buttonWidth=dp(75);
        buttonOverlay(c,"CLOSE",w-buttonWidth-pad,y+dp(8),buttonWidth,dp(42),()->showModChooser=false);
        float start=y+dp(63);
        int columns=3;float gap=dp(5),bw=(w-pad*2-gap*2)/3;
        for(int i=0;i<LoomEngine.MOD_NAMES.length;i++){
            final int source=i;float x=pad+(i%3)*(bw+gap),yy=start+(i/3)*dp(49);
            buttonOverlay(c,LoomEngine.MOD_NAMES[i],x,yy,bw,dp(44),()->{
                engine.assignMod(source,selectedDest,.4f);
                selectedRoute=-1;
                for(int j=0;j<LoomEngine.ROUTES;j++)
                    if(engine.routeSource[j]==source&&engine.routeDest[j]==selectedDest){selectedRoute=j;break;}
                showModChooser=false;
                info="ASSIGNED "+LoomEngine.MOD_NAMES[source];
            });
        }
    }
    private void buttonOverlay(Canvas c,String str,float x,float y,float w,float h,Runnable action){
        rect(c,PANEL,x,y,w,h);line(c,BORDER,x,y,w,h);
        center(c,str,x,y,w,h,1.5f,TEXT);
        zones.add(new Zone(new RectF(x,y,x+w,y+h),KIND_BUTTON,-1,action));
    }
    private void drawContext(Canvas c,float w){
        float pad=dp(10),overlayH=dp(220),y=bodyBottom-overlayH;
        rect(c,BG,0,y,w,overlayH);line(c,CYAN,pad,y,w-2*pad,overlayH);
        label(c,fit(LoomEngine.PARAM_NAMES[contextParam],w-dp(20),1.6f),
            pad+dp(7),y+dp(14),1.6f,CYAN);
        float half=(w-dp(25))/2;
        buttonOverlay(c,"RESET",pad,y+dp(49),half,dp(49),()->{
            engine.setParam(contextParam,.5f);showContext=false;
        });
        buttonOverlay(c,"MOD ASSIGN",pad+half+dp(5),y+dp(49),half,dp(49),()->{
            selectedDest=contextParam;showContext=false;showModChooser=true;mode=MOD;
        });
        buttonOverlay(c,"MIDI LEARN",pad,y+dp(107),half,dp(49),()->{
            engine.armMidiLearn(contextParam);mode=LEARN;showContext=false;
        });
        buttonOverlay(c,"CLEAR LOCK",pad+half+dp(5),y+dp(107),half,dp(49),()->{
            engine.clearNoteLock(engine.noteLockTarget,contextParam);showContext=false;
        });
        buttonOverlay(c,"CLOSE",pad,y+dp(169),w-2*pad,dp(42),()->showContext=false);
    }
    private void updateXY(float x,float y,RectF rect){
        engine.xyX=Math.max(0,Math.min(1,(x-rect.left)/Math.max(1,rect.width())));
        engine.xyY=Math.max(0,Math.min(1,1-(y-rect.top)/Math.max(1,rect.height())));
    }
    private void updateParam(Zone z,float x,boolean initial){
        int id=z.param;
        if(mode==MOD||mode==LEARN)return;
        if(mode==LOCK&&engine.noteLockTarget<0)return;
        float value;
        if(mode==FINE)value=dragStartValue+(x-dragStartX)/Math.max(1,z.box.width())*.15f;
        else value=(x-z.box.left)/Math.max(1,z.box.width());
        engine.setParam(id,value);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked(),idx=event.getActionIndex();
        int pointer=event.getPointerId(idx),count=event.getPointerCount();
        float x=event.getX(idx),y=event.getY(idx);
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            if(y>=keyboardTop){handleKeyboard(pointer,x,y,false);invalidate();return true;}
            if(action==MotionEvent.ACTION_DOWN){
                dragging=zoneAt(x,y);dragStartX=x;dragStartY=lastY=y;
                downTime=event.getEventTime();moved=false;
                if(dragging!=null&&dragging.kind==KIND_PARAM){
                    dragStartValue=engine.getParam(dragging.param);
                    if(mode==NORMAL||mode==FINE||mode==LOCK)
                        updateParam(dragging,x,true);
                }
                if(dragging!=null&&dragging.kind==KIND_XY)updateXY(x,y,dragging.box);
                if(dragging!=null&&dragging.kind==KIND_MORPH)engine.setStateMorph((x-dragging.box.left)/dragging.box.width());
            }
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            for(int i=0;i<count;i++){
                int id=event.getPointerId(i);
                if(activeKeys.indexOfKey(id)>=0)handleKeyboard(id,event.getX(i),event.getY(i),false);
            }
            if(count>0){
                x=event.getX(0);y=event.getY(0);
                float dx=x-dragStartX,dy=y-dragStartY;
                if(Math.abs(dx)>dp(8)||Math.abs(dy)>dp(8))moved=true;
                if(dragging!=null&&dragging.kind==KIND_PARAM){
                    if(mode==NORMAL||mode==FINE||mode==LOCK)updateParam(dragging,x,false);
                }else if(dragging!=null&&dragging.kind==KIND_XY){
                    updateXY(x,y,dragging.box);
                }else if(dragging!=null&&dragging.kind==KIND_MORPH){
                    engine.setStateMorph((x-dragging.box.left)/dragging.box.width());
                }else if(dragging==null&&y>bodyTop&&y<bodyBottom){
                    scroll=Math.max(0,Math.min(Math.max(0,contentHeight-(bodyBottom-bodyTop)),scroll-(y-lastY)));
                }
                lastY=y;
            }
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_CANCEL){
            handleKeyboard(pointer,x,y,true);
            if(action==MotionEvent.ACTION_UP&&dragging!=null){
                boolean longPress=(event.getEventTime()-downTime)>550&&!moved;
                if(dragging.kind==KIND_PARAM){
                    if(longPress)showAction(dragging.param);
                    else if(!moved||mode==MOD||mode==LEARN)selectParam(dragging.param);
                }else if(dragging.kind==KIND_BUTTON&&!moved&&dragging.box.contains(x,y)){
                    dragging.click.run();
                }
                dragging=null;
            }
            if(action==MotionEvent.ACTION_CANCEL)releaseKeys();
            invalidate();return true;
        }
        return true;
    }
}
