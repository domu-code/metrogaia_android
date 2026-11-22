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
import java.util.List;
import java.util.Locale;

/** All layout uses actual View dimensions and density, not desktop coordinates. */
public final class SynthView extends View {
    public interface PatchHandler{
        void save();void load();void next();void previous();int index();
    }
    private interface SliderValue{
        float get();void set(float value);
    }
    private interface ZoneAction{void perform(float fraction);}
    private static final class Zone{
        RectF rect;ZoneAction action;boolean continuous;
        Zone(RectF bounds,ZoneAction a,boolean drag){rect=bounds;action=a;continuous=drag;}
    }
    private final Paint paint=new Paint();
    private final PixelFont font=new PixelFont();
    private final SynthEngine engine;
    private final AndroidMidi midi;
    private final boolean loom;
    private final PatchHandler patches;
    private final List<Zone> zones=new ArrayList<>();
    private final SparseIntArray notes=new SparseIntArray();
    private static final String[] TABS={"PLAY","OSC","FILTER","FX","MIDI"};
    private static final int[] WHITE={0,2,4,5,7,9,11};
    private static final boolean[] BLACK_AFTER={true,true,false,true,true,true,false};
    private final int bg=Color.rgb(15,19,23), panel=Color.rgb(29,35,40);
    private final int white=Color.rgb(222,229,225),muted=Color.rgb(135,150,152);
    private final int accent;
    private final float density;
    private int page=0;
    private float scroll=0,contentHeight=0,bodyTop,bodyBottom,keyTop,keyBottom;
    private Zone captured=null;
    private float downY,lastY;
    private boolean dragged=false;

    public SynthView(Context ctx,SynthEngine e,AndroidMidi m,boolean isLoom,PatchHandler p){
        super(ctx);
        engine=e;midi=m;loom=isLoom;patches=p;
        density=ctx.getResources().getDisplayMetrics().density;
        accent=isLoom?Color.rgb(98,212,197):Color.rgb(255,173,65);
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        setFocusable(true);setFocusableInTouchMode(true);
    }
    private float dp(float x){return x*density;}
    private void fill(Canvas c,int color,float l,float t,float r,float b){
        paint.setStyle(Paint.Style.FILL);paint.setColor(color);
        c.drawRect(l,t,r,b,paint);
    }
    private void outline(Canvas c,int color,float l,float t,float r,float b){
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));
        c.drawRect(l,t,r,b,paint);paint.setStyle(Paint.Style.FILL);
    }
    private void txt(Canvas c,String text,float x,float y,float unit,int color){
        font.draw(c,text,x,y,dp(unit),color);
    }
    private void register(RectF area,ZoneAction action,boolean continuous){
        zones.add(new Zone(area,action,continuous));
    }
    private void button(Canvas c,String name,float x,float y,float w,float h,boolean selected,Runnable action){
        if(y+h<bodyTop||y>bodyBottom)return;
        fill(c,selected?accent:panel,x,y,x+w,y+h);
        outline(c,selected?accent:muted,x,y,x+w,y+h);
        float px=Math.max(dp(1.3f),Math.min(dp(2),w/(Math.max(1,name.length())*6.6f)));
        float length=font.width(name,px);
        font.draw(c,name,x+(w-length)/2f,y+(h-5*px)/2f,px,selected?bg:white);
        register(new RectF(x,y,x+w,y+h),f->action.run(),false);
    }
    private void slider(Canvas c,String label,float x,float y,float w,float h,SliderValue param){
        if(y+h<bodyTop||y>bodyBottom)return;
        fill(c,panel,x,y,x+w,y+h);
        float v=Math.max(0,Math.min(1,param.get()));
        txt(c,label,x+dp(10),y+dp(7),1.8f,white);
        String value=String.format(Locale.US,"%03d",(int)(v*100));
        float size=dp(1.8f);
        font.draw(c,value,x+w-dp(10)-font.width(value,size),y+dp(7),size,accent);
        float left=x+dp(10),right=x+w-dp(10),track=y+h-dp(21);
        fill(c,Color.rgb(70,85,86),left,track,right,track+dp(8));
        fill(c,accent,left,track,left+(right-left)*v,track+dp(8));
        register(new RectF(x,y,x+w,y+h),f->param.set(f),true);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        if(w<1||h<1)return;
        zones.clear();
        fill(c,bg,0,0,w,h);
        float head=dp(54),tabs=dp(48);
        float keyHeight=Math.min(dp(164),h*.30f);
        keyTop=h-keyHeight;keyBottom=h;
        bodyTop=head+tabs;
        bodyBottom=keyTop-dp(3);
        float edge=dp(8);
        txt(c,loom?"LOOM / ANDROID":"METROGAIA / ANDROID",dp(12),dp(11),2.6f,accent);
        txt(c,"12 VOICE / MIDI / V0.1",dp(12),dp(37),1.35f,muted);
        float meter=engine.meter();
        fill(c,Color.rgb(50,59,62),w-dp(87),dp(18),w-dp(12),dp(26));
        fill(c,accent,w-dp(87),dp(18),w-dp(87)+dp(75)*Math.min(1,meter),dp(26));
        int n=TABS.length;
        float tabw=w/n;
        for(int i=0;i<n;i++){
            final int which=i;
            float x=i*tabw;
            buttonUnclipped(c,TABS[i],x+dp(2),head+dp(2),tabw-dp(4),tabs-dp(5),page==i,
                ()->{page=which;scroll=0;invalidate();});
        }
        fill(c,Color.rgb(42,52,56),0,bodyBottom,w,bodyBottom+dp(3));
        c.save();
        c.clipRect(0,bodyTop,w,bodyBottom);
        float y=bodyTop+edge-scroll;
        int columns=(w/density>=640)?2:1;
        float gap=dp(7),colW=(w-edge*2-gap*(columns-1))/columns;
        switch(page){
            case 0:{
                txt(c,"PATCH BANK",edge,y+dp(4),2,accent);y+=dp(28);
                float bw=(w-edge*2-gap*3)/4;
                button(c,"<",edge,y,bw,dp(46),false,patches::previous);
                button(c,"P "+(patches.index()+1),edge+(bw+gap),y,bw,dp(46),true,patches::load);
                button(c,">",edge+2*(bw+gap),y,bw,dp(46),false,patches::next);
                button(c,"SAVE",edge+3*(bw+gap),y,bw,dp(46),false,patches::save);
                y+=dp(56);
                txt(c,"OSCILLATOR MIX",edge,y,2,accent);y+=dp(27);
                for(int i=0;i<4;i++){
                    final int j=i;
                    int col=i%columns,row=i/columns;
                    slider(c,"OSC "+(i+1),edge+col*(colW+gap),y+row*dp(63),colW,dp(57),
                           new SliderValue(){public float get(){return engine.levels[j];}
                           public void set(float v){engine.levels[j]=v;}});
                }
                y+=((4+columns-1)/columns)*dp(63)+dp(10);
                txt(c,"PLAY KEYS BELOW / USB MIDI READY",edge,y,1.65f,muted);
                y+=dp(32);
                break;
            }
            case 1:{
                for(int i=0;i<4;i++){
                    final int index=i;
                    int col=i%columns,row=i/columns;
                    float x=edge+col*(colW+gap),cy=y+row*dp(152);
                    fill(c,panel,x,cy,x+colW,cy+dp(145));
                    txt(c,"OSC "+(i+1),x+dp(9),cy+dp(9),2.2f,accent);
                    float btnW=colW*.52f;
                    button(c,SynthEngine.WAVES[engine.waves[i]],x+dp(10),cy+dp(37),btnW-dp(12),dp(43),true,
                        ()->{engine.waves[index]=(engine.waves[index]+1)%SynthEngine.WAVES.length;});
                    button(c,"-",x+btnW,cy+dp(37),(colW-btnW)/2-dp(6),dp(43),false,
                        ()->{engine.cents[index]=Math.max(-1200,engine.cents[index]-25);});
                    button(c,"+",x+btnW+(colW-btnW)/2,cy+dp(37),(colW-btnW)/2-dp(10),dp(43),false,
                        ()->{engine.cents[index]=Math.min(1200,engine.cents[index]+25);});
                    slider(c,"LEVEL",x+dp(10),cy+dp(87),colW-dp(20),dp(50),
                        new SliderValue(){public float get(){return engine.levels[index];}
                        public void set(float v){engine.levels[index]=v;}});
                }
                y+=((4+columns-1)/columns)*dp(152)+dp(10);
                break;
            }
            case 2:{
                float start=y;
                String[] labels={"CUTOFF","RESONANCE","ATTACK","DECAY","SUSTAIN","RELEASE"};
                for(int i=0;i<labels.length;i++){
                    final int j=i;
                    int col=i%columns,row=i/columns;
                    slider(c,labels[i],edge+col*(colW+gap),start+row*dp(66),colW,dp(60),
                        new SliderValue(){
                            public float get(){
                                switch(j){
                                    case 0:return engine.cutoff;case 1:return engine.resonance;
                                    case 2:return engine.attack;case 3:return engine.decay;
                                    case 4:return engine.sustain;default:return engine.release;
                                }
                            }
                            public void set(float v){
                                switch(j){
                                    case 0:engine.cutoff=v;break;case 1:engine.resonance=v;break;
                                    case 2:engine.attack=v;break;case 3:engine.decay=v;break;
                                    case 4:engine.sustain=v;break;default:engine.release=v;break;
                                }
                            }
                        });
                }
                y+=((6+columns-1)/columns)*dp(66)+dp(9);
                break;
            }
            case 3:{
                String[] names={"DELAY TIME","FEEDBACK","DRIVE","MASTER"};
                for(int i=0;i<4;i++){
                    final int j=i;int col=i%columns,row=i/columns;
                    slider(c,names[i],edge+col*(colW+gap),y+row*dp(72),colW,dp(63),
                        new SliderValue(){
                            public float get(){
                                return j==0?engine.delay:j==1?engine.feedback:j==2?engine.drive:engine.master;
                            }
                            public void set(float v){
                                if(j==0)engine.delay=v;else if(j==1)engine.feedback=v;
                                else if(j==2)engine.drive=v;else engine.master=v;
                            }
                        });
                }
                y+=((4+columns-1)/columns)*dp(72)+dp(10);
                txt(c,"STEREO ECHO / SATURATION",edge,y,1.75f,muted);y+=dp(28);
                break;
            }
            case 4:{
                txt(c,"ANDROID MIDI INPUT",edge,y,2,accent);y+=dp(29);
                button(c,"NEXT MIDI PORT",edge,y,w-2*edge,dp(50),true,midi::next);y+=dp(58);
                String name=midi.label();
                int max=(int)((w-2*edge)/dp(1.6f)/6);
                txt(c,name.substring(0,Math.min(name.length(),Math.max(2,max))),edge,y,1.6f,white);
                y+=dp(35);
                button(c,"RESCAN USB / MIDI",edge,y,w-2*edge,dp(50),false,midi::refresh);y+=dp(65);
                txt(c,"INPUT: USB / ANDROID VIRTUAL",edge,y,1.7f,accent);y+=dp(30);
                txt(c,"OMNI / ALL 16 MIDI CHANNELS",edge,y,1.7f,white);y+=dp(27);
                txt(c,"CC 1 MOD / 7 GAIN / 74 CUT",edge,y,1.7f,white);y+=dp(27);
                txt(c,"CC 71 RES / 64 SUSTAIN",edge,y,1.7f,white);y+=dp(27);
                txt(c,"PITCH BEND / NOTES / VELOCITY",edge,y,1.7f,white);y+=dp(27);
                txt(c,"KEEP APP OPEN FOR AUDIO",edge,y,1.7f,muted);y+=dp(28);
                break;
            }
        }
        contentHeight=Math.max(0,y+scroll-bodyTop+dp(8));
        c.restore();
        drawKeyboard(c,w);
        postInvalidateDelayed(40);
    }
    private void buttonUnclipped(Canvas c,String label,float x,float y,float w,float h,boolean selected,Runnable run){
        fill(c,selected?accent:panel,x,y,x+w,y+h);
        float p=Math.min(dp(1.9f),w/(label.length()*7f));
        font.draw(c,label,x+(w-font.width(label,p))/2,y+(h-p*5)/2,p,selected?bg:white);
        register(new RectF(x,y,x+w,y+h),f->run.run(),false);
    }
    private int whiteCount;
    private float whiteWidth;
    private void drawKeyboard(Canvas c,float width){
        whiteCount=(width/density>=640)?14:7;
        whiteWidth=width/whiteCount;
        fill(c,panel,0,keyTop,width,keyBottom);
        for(int i=0;i<whiteCount;i++){
            int note=noteForWhite(i);
            float x=i*whiteWidth;
            boolean held=held(note);
            fill(c,held?accent:white,x+dp(1),keyTop+dp(5),x+whiteWidth-dp(1),keyBottom-dp(3));
            if(i%7==0)txt(c,(i==0?"C4":"C5"),x+dp(6),keyBottom-dp(23),1.5f,bg);
        }
        for(int i=0;i<whiteCount;i++){
            if(!BLACK_AFTER[i%7])continue;
            int black=noteForWhite(i)+1;
            float x=(i+1)*whiteWidth-whiteWidth*.27f;
            fill(c,held(black)?accent:bg,x,keyTop+dp(5),x+whiteWidth*.54f,keyTop+(keyBottom-keyTop)*.60f);
        }
        outline(c,muted,0,keyTop,width,keyBottom);
    }
    private int noteForWhite(int i){return 60+WHITE[i%7]+(i/7)*12;}
    private boolean held(int note){
        for(int i=0;i<notes.size();i++)if(notes.valueAt(i)==note)return true;
        return false;
    }
    private int keyAt(float x,float y){
        if(y<keyTop||y>keyBottom)return -1;
        for(int i=0;i<whiteCount;i++)if(BLACK_AFTER[i%7]){
            float left=(i+1)*whiteWidth-whiteWidth*.27f;
            if(x>=left&&x<left+whiteWidth*.54f&&y<keyTop+(keyBottom-keyTop)*.60f)
                return noteForWhite(i)+1;
        }
        int i=(int)(x/whiteWidth);
        return i<0||i>=whiteCount?-1:noteForWhite(i);
    }
    private Zone zoneAt(float x,float y){
        for(int i=zones.size()-1;i>=0;i--)if(zones.get(i).rect.contains(x,y))return zones.get(i);
        return null;
    }
    private void handleKey(int id,float x,float y,boolean release){
        int old=notes.get(id,-1);
        int note=release?-1:keyAt(x,y);
        if(old==note)return;
        if(old>=0)engine.noteOff(old);
        notes.delete(id);
        if(note>=0){notes.put(id,note);engine.noteOn(note,105);}
        invalidate();
    }
    public void releaseKeys(){
        for(int i=0;i<notes.size();i++)engine.noteOff(notes.valueAt(i));
        notes.clear();
    }
    @Override public boolean onTouchEvent(MotionEvent ev){
        int action=ev.getActionMasked(),index=ev.getActionIndex(),id=ev.getPointerId(index);
        float x=ev.getX(index),y=ev.getY(index);
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            if(y>=keyTop){handleKey(id,x,y,false);return true;}
            if(action==MotionEvent.ACTION_DOWN){
                captured=zoneAt(x,y);downY=y;lastY=y;dragged=false;
                if(captured!=null&&captured.continuous)applySlider(captured,x);
            }
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            for(int j=0;j<ev.getPointerCount();j++){
                int pid=ev.getPointerId(j);
                if(notes.indexOfKey(pid)>=0)handleKey(pid,ev.getX(j),ev.getY(j),false);
            }
            if(captured!=null&&captured.continuous&&ev.getPointerId(0)>=0){
                applySlider(captured,ev.getX(0));
            }else if(ev.getPointerCount()>0&&ev.getY(0)<keyTop&&ev.getY(0)>bodyTop){
                float delta=ev.getY(0)-lastY;
                if(Math.abs(ev.getY(0)-downY)>dp(8))dragged=true;
                if(dragged){
                    scroll=Math.max(0,Math.min(Math.max(0,contentHeight-(bodyBottom-bodyTop)),scroll-delta));
                    invalidate();
                }
                lastY=ev.getY(0);
            }
            return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_CANCEL){
            handleKey(id,x,y,true);
            if(action==MotionEvent.ACTION_UP){
                if(captured!=null&&!captured.continuous&&!dragged&&captured.rect.contains(x,y)){
                    captured.action.perform(0);
                    invalidate();
                }
                captured=null;
            }
            if(action==MotionEvent.ACTION_CANCEL)releaseKeys();
            return true;
        }
        return true;
    }
    private void applySlider(Zone z,float x){
        float fraction=(x-z.rect.left)/Math.max(1,z.rect.width());
        z.action.perform(Math.max(0,Math.min(1,fraction)));
        invalidate();
    }
}
