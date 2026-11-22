package com.domucode.synth;

import java.util.ArrayList;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;

/** LOOM mobile synthesis core. Eight distinct real-time source families.
 * Android-native Java implementations inspired by the desktop M7.5.4 project.
 * The specialized algorithms are mobile adaptations, not byte-identical ports.
 */
public final class LoomEngine extends SynthEngine {
    public static final int ENGINE_COUNT=8;
    public static final int NUM=64;
    public static final String[] ENGINE_NAMES={
        "PLAITS","BRAIDS","DMG","SAMPLER","SPEECH","RINGS","HANDPAN","AIR BORE"};
    public static final String[] PARAM_NAMES=new String[NUM];
    public static final String[] MOD_NAMES={
        "LFO 1","LFO 2","ENV 2","AMP ENV","VELOCITY","MOD WHEEL",
        "PITCH BEND","XY X","XY Y","STEP","RANDOM","AFTERTOUCH"};
    public static final String[] FX_NAMES={"DRIVE","BITCRUSH","CHORUS","DELAY","REVERB"};
    public static final int P_FILTER=40,P_RESO=41,P_ATTACK=42,P_DECAY=43,P_SUSTAIN=44,
        P_RELEASE=45,P_MASTER=46,P_DRIVE=47,P_CRUSH=48,P_CHORUS=49,P_DELAY=50,
        P_REVERB=51,P_TEMPO=52,P_ARPGATE=53,P_LFO1=54,P_LFO2=55,
        P_LFODEPTH1=56,P_LFODEPTH2=57,P_RINGDAMP=58,P_MATERIAL=59,
        P_BREATH=60,P_GRAIN=61,P_SAMPLESTART=62,P_XYDEPTH=63;
    public static final int ROUTES=24;
    static {
        for(int i=0;i<8;i++){
            PARAM_NAMES[i]=ENGINE_NAMES[i]+" LEVEL";
            PARAM_NAMES[8+i]=ENGINE_NAMES[i]+" MODEL";
            PARAM_NAMES[16+i]=ENGINE_NAMES[i]+" HARM";
            PARAM_NAMES[24+i]=ENGINE_NAMES[i]+" TIMBRE";
            PARAM_NAMES[32+i]=ENGINE_NAMES[i]+" MORPH";
        }
        String[] extra={"FILTER CUTOFF","RESONANCE","ATTACK","DECAY","SUSTAIN","RELEASE",
            "MASTER","DRIVE","BITCRUSH","CHORUS","DELAY","REVERB","TEMPO","ARP GATE",
            "LFO 1 RATE","LFO 2 RATE","LFO 1 DEPTH","LFO 2 DEPTH",
            "RING DAMP","HANDPAN MATERIAL","AIR PRESSURE","GRAIN AMOUNT",
            "SAMPLE START","XY DEPTH"};
        System.arraycopy(extra,0,PARAM_NAMES,40,extra.length);
    }
    public final float[] values=new float[NUM];
    public final int[] ccMappings=new int[128];
    public final int[] routeSource=new int[ROUTES],routeDest=new int[ROUTES];
    public final float[] routeDepth=new float[ROUTES];
    public final boolean[] fxEnabled={true,false,true,true,true};
    public final int[] fxOrder={0,1,2,3,4};
    public final boolean[] stepEnabled=new boolean[16];
    public final int[] stepNotes=new int[16];
    public volatile int pendingLearn=-1;
    public volatile int noteLockTarget=-1;
    public volatile boolean noteLockMode=false,seqEnabled=false,arpEnabled=false;
    public volatile boolean looperOverdub=false;
    public volatile int activeBank=0,activeSlot=0,activeOctave=4;
    public volatile float xyX=.5f,xyY=.5f,aftertouch=0f;
    public volatile float pitchBend=0f,modWheel=0f;
    public final LoopSlot[][] loops=new LoopSlot[8][8];
    private final float[] modulated=new float[NUM];
    private final Voice[] voices=new Voice[10];
    private final float[] delayL,delayR,revL,revR;
    private final int rate;
    private int delayPtr,revPtr,voicesActive,heldNote=-1,arpTick;
    private long frames,voiceSerial;
    private int sequenceStep;
    private float lfoPhase1,lfoPhase2,randomValue=0.25f,vu;
    private int randomSeed=0x4567a1b3;
    private int playingNote=-1;
    private long stepClock=0,arpClock=0;
    private final boolean[] noteHeld=new boolean[128];
    private final int[] sampleVelocity=new int[128];
    private volatile float[] sampleData;
    private volatile int sampleRate=44100;
    public volatile String sampleName="NONE";
    private final float[] perNoteLocks=new float[128*NUM];
    private final boolean[] locked=new boolean[128*NUM];

    public static final class LoopEvent {
        public final long offset;
        public final int status,a,b;
        LoopEvent(long o,int s,int x,int y){offset=o;status=s;a=x;b=y;}
    }
    public static final class LoopSlot {
        public final ArrayList<LoopEvent> events=new ArrayList<>();
        public long start,length=1;
        public int bars=1;
        public boolean recording,playing,overdub,enabled=true;
        public int playIndex;
        public long previousPhase=-1;
    }
    private static final class Voice {
        boolean active,gate;
        int note,rand,age;
        long stamp;
        float velocity,env,env2,low,band,airEnv,airLp,airDC;
        final double[] phase=new double[ENGINE_COUNT*4];
        final float[] modal=new float[12],modalB=new float[12];
        final float[] panModal=new float[12],panModalB=new float[12];
        final float[] ring=new float[2048],air=new float[4096];
        int ringPtr,ringLength,airPtr,airLength;
        float samplePosition;
        float sampleGrainPhase=0;
        Voice(){rand=0x71343ac1;}
    }
    public LoomEngine(int sampleRate){
        super(sampleRate);
        rate=sampleRate;
        for(int i=0;i<voices.length;i++)voices[i]=new Voice();
        for(int b=0;b<8;b++)for(int s=0;s<8;s++)loops[b][s]=new LoopSlot();
        delayL=new float[rate*2];delayR=new float[rate*2];
        revL=new float[Math.max(8000,rate/3)];revR=new float[Math.max(8000,rate/4)];
        Arrays.fill(ccMappings,-1);
        Arrays.fill(routeSource,-1);
        Arrays.fill(stepNotes,60);
        stepEnabled[0]=stepEnabled[4]=stepEnabled[8]=stepEnabled[12]=true;
        presetMobile(0);
    }
    public synchronized void presetMobile(int number){
        Arrays.fill(values,.5f);
        float[][] lev={
            {.85f,.30f,.18f,0,.0f,.14f,0,0},
            {.25f,.77f,.15f,0,0,.1f,0,0},
            {0,0,.88f,.0f,0,0,0,0},
            {0,0,0,.86f,0,0,0,0},
            {.1f,.12f,0,0,.90f,0,0,0},
            {.16f,0,0,0,0,.84f,.33f,0},
            {.07f,0,0,0,0,.16f,.90f,0},
            {0,0,0,0,0,.12f,0,.92f}};
        int p=Math.floorMod(number,8);
        for(int i=0;i<8;i++){values[i]=lev[p][i];values[8+i]=((p*7+i*5)%25)/49f;values[16+i]=.40f;values[24+i]=.48f;values[32+i]=.35f;}
        values[P_FILTER]=.76f;values[P_RESO]=.18f;
        values[P_ATTACK]=(p==4||p==3)?.45f:.045f;
        values[P_DECAY]=.30f;values[P_SUSTAIN]=.65f;values[P_RELEASE]=.4f;
        values[P_MASTER]=.72f;values[P_DRIVE]=.15f;values[P_CRUSH]=0;
        values[P_CHORUS]=.12f;values[P_DELAY]=.20f;values[P_REVERB]=.24f;
        values[P_TEMPO]=.50f;values[P_ARPGATE]=.64f;
        values[P_LFO1]=.35f;values[P_LFO2]=.15f;values[P_LFODEPTH1]=.4f;values[P_LFODEPTH2]=.4f;
        values[P_RINGDAMP]=.5f;values[P_MATERIAL]=.25f;values[P_BREATH]=.65f;
        values[P_GRAIN]=.2f;values[P_SAMPLESTART]=0;values[P_XYDEPTH]=.5f;
        Arrays.fill(routeSource,-1);Arrays.fill(routeDepth,0);Arrays.fill(routeDest,0);
        allNotesOff();
    }
    private float clamp(float x){return Math.max(0,Math.min(1,x));}
    public synchronized void setParam(int p,float value){
        if(p<0||p>=NUM)return;
        float v=clamp(value);
        if(noteLockMode&&noteLockTarget>=0){
            locked[noteLockTarget*NUM+p]=true;
            perNoteLocks[noteLockTarget*NUM+p]=v;
        }else values[p]=v;
    }
    public synchronized float getParam(int p){
        if(p<0||p>=NUM)return 0;
        if(noteLockMode&&noteLockTarget>=0&&locked[noteLockTarget*NUM+p])
            return perNoteLocks[noteLockTarget*NUM+p];
        return values[p];
    }
    public synchronized void clearNoteLock(int note,int param){
        if(note>=0&&note<128&&param>=0&&param<NUM)locked[note*NUM+param]=false;
    }
    public synchronized boolean hasLock(int note,int param){
        return note>=0&&note<128&&param>=0&&param<NUM&&locked[note*NUM+param];
    }
    public synchronized void assignMod(int source,int destination,float depth){
        if(source<0||source>=MOD_NAMES.length||destination<0||destination>=NUM)return;
        int target=-1;
        for(int i=0;i<ROUTES;i++)
            if(routeSource[i]==source&&routeDest[i]==destination){target=i;break;}
        if(target<0)for(int i=0;i<ROUTES;i++)if(routeSource[i]<0){target=i;break;}
        if(target>=0){
            routeSource[target]=source;routeDest[target]=destination;routeDepth[target]=Math.max(-1,Math.min(1,depth));
        }
    }
    public synchronized void deleteMod(int index){
        if(index>=0&&index<ROUTES){routeSource[index]=-1;routeDepth[index]=0;}
    }
    public synchronized void armMidiLearn(int param){pendingLearn=param;}
    @Override public synchronized void cc(int cc,int data){
        if(cc<0||cc>=128)return;
        if(pendingLearn>=0){ccMappings[cc]=pendingLearn;pendingLearn=-1;return;}
        int p=ccMappings[cc];
        if(p>=0){setParam(p,data/127f);return;}
        float n=data/127f;
        if(cc==1)modWheel=n;
        else if(cc==7)setParam(P_MASTER,n);
        else if(cc==71)setParam(P_RESO,n);
        else if(cc==74)setParam(P_FILTER,n);
        else if(cc==73)setParam(P_ATTACK,n);
        else if(cc==72)setParam(P_RELEASE,n);
        else if(cc==91)setParam(P_REVERB,n);
        else if(cc==93)setParam(P_DELAY,n);
        else if(cc==120||cc==123)allNotesOff();
        else if(cc==64&&data<64)for(Voice v:voices)if(v.active&&!noteHeld[v.note])v.gate=false;
        capture(0xb0,cc,data);
    }
    @Override public synchronized void pitchBend(int bend){
        pitchBend=Math.max(-1,Math.min(1,(bend-8192)/8192f));
        capture(0xe0,bend&127,(bend>>7)&127);
    }
    public synchronized void pressure(int amount){aftertouch=clamp(amount/127f);}
    @Override public synchronized void noteOn(int note,int velocity){
        if(note<0||note>=128||velocity<=0)return;
        noteHeld[note]=true;sampleVelocity[note]=velocity;
        if(noteLockMode)noteLockTarget=note;
        if(!arpEnabled)trigger(note,velocity);
        else if(heldNote<0)heldNote=note;
        capture(0x90,note,velocity);
    }
    private void trigger(int note,int velocity){
        Voice v=null;
        for(Voice one:voices)if(!one.active){v=one;break;}
        if(v==null){v=voices[0];for(Voice one:voices)if(one.stamp<v.stamp)v=one;}
        v.active=true;v.gate=true;v.note=note;v.velocity=velocity/127f;v.env=0;v.env2=0;
        v.low=0;v.band=0;v.age=0;v.airEnv=0;v.airLp=0;v.airDC=0;
        v.stamp=++voiceSerial;v.samplePosition=sampleData==null?0:sampleData.length*values[P_SAMPLESTART];
        v.sampleGrainPhase=0;v.ringPtr=v.airPtr=0;v.rand=randomSeed++;
        double hz=440*Math.pow(2,(note-69)/12.0);
        v.ringLength=Math.max(12,Math.min(v.ring.length-1,(int)(rate/hz)));
        v.airLength=Math.max(12,Math.min(v.air.length-1,(int)(rate/(hz*1.85))));
        for(int i=0;i<v.phase.length;i++)v.phase[i]=0;
        Arrays.fill(v.modal,0);Arrays.fill(v.modalB,0);
        Arrays.fill(v.panModal,0);Arrays.fill(v.panModalB,0);
        for(int i=0;i<v.ringLength;i++)v.ring[i]=noise(v)*v.velocity*.6f;
        Arrays.fill(v.air,0);
    }
    @Override public synchronized void noteOff(int note){
        if(note<0||note>127)return;
        noteHeld[note]=false;
        if(!arpEnabled)for(Voice v:voices)if(v.active&&v.note==note)v.gate=false;
        if(heldNote==note)heldNote=-1;
        capture(0x80,note,0);
    }
    @Override public synchronized void allNotesOff(){
        for(Voice v:voices)v.gate=v.active=false;
        Arrays.fill(noteHeld,false);heldNote=-1;
    }
    private float noise(Voice v){
        int x=v.rand;x^=x<<13;x^=x>>>17;x^=x<<5;v.rand=x;
        return ((x>>>8)/8388608f)-1f;
    }
    private double freq(Voice v){
        return 440*Math.pow(2,(v.note-69+pitchBend*2)/12.0);
    }
    private double advance(Voice v,int idx,double f){
        double p=v.phase[idx]+f/rate;
        p-=Math.floor(p);v.phase[idx]=p;
        return p;
    }
    private double advance(Voice v,int idx,double f,int ignored){return advance(v,idx,f);}
    private float phasor(Voice v,int idx,double f,int shape){
        double p=advance(v,idx,f);
        if(shape==0)return (float)Math.sin(2*Math.PI*p);
        if(shape==1)return (float)(2*p-1);
        if(shape==2)return (float)(1-4*Math.abs(p-.5));
        return p<.5?1:-1;
    }
    private float parameter(int id,Voice v){
        float value=modulated[id];
        if(v!=null&&locked[v.note*NUM+id])value=perNoteLocks[v.note*NUM+id];
        return clamp(value);
    }
    private float genPlaits(Voice v,double hz){
        int model=(int)(parameter(8,v)*23.99f);
        float h=parameter(16,v),t=parameter(24,v),m=parameter(32,v);
        double p=advance(v,0,hz);
        switch(model){
            case 0:return .7f*phasor(v,1,hz,1)+.3f*phasor(v,2,hz*.5,3);
            case 1:return (float)Math.sin(2*Math.PI*(p+(t*.3)*Math.sin(2*Math.PI*p*2)));
            case 2:case 3:case 4:return (float)Math.sin(2*Math.PI*p+3*t*Math.sin(2*Math.PI*advance(v,1,hz*(1+model))));
            case 5:return (float)Math.sin(2*Math.PI*p*(1+Math.sin(2*Math.PI*advance(v,1,hz*.25,1))*h*2));
            case 6:return .4f*phasor(v,1,hz,1)+.3f*phasor(v,2,hz*2,2)+.3f*phasor(v,3,hz*4,0);
            case 7:return phasor(v,1,hz,3)*(m<.5f?1f:.5f);
            case 8:return (1-t)*phasor(v,1,hz,1)+t*phasor(v,2,hz,3);
            case 9:return (float)Math.tanh(4*t*Math.sin(2*Math.PI*p));
            case 10:return (float)Math.sin(2*Math.PI*p+5*h*Math.sin(2*Math.PI*advance(v,1,hz*2,0)));
            case 11:return (float)Math.sin(2*Math.PI*p)*((float)Math.sin(2*Math.PI*advance(v,1,300+h*2000,0)));
            case 12:return (float)(Math.sin(2*Math.PI*p)+t*Math.sin(6*Math.PI*p)+m*Math.sin(12*Math.PI*p))*.5f;
            case 13:return phasor(v,1,hz,2)*.7f+phasor(v,2,hz*1.005,1)*.3f;
            case 14:return (phasor(v,1,hz,0)+phasor(v,2,hz*1.5,0)+phasor(v,3,hz*2,0))*.33f;
            case 15:return genSpeech(v,hz);
            case 16:return .4f*phasor(v,1,hz,1)+.3f*phasor(v,2,hz*1.009,1)+.3f*phasor(v,3,hz*.994,1);
            case 17:return noise(v)*.55f;
            case 18:return noise(v)*(v.age%150<8?1:.04f);
            case 19:return genRings(v,hz);
            case 20:return genHandpan(v,hz);
            case 21:return (float)Math.sin(2*Math.PI*advance(v,1,hz*(1+Math.exp(-v.age/(rate*.07)))*2,0))*v.env;
            case 22:return noise(v)*v.env;
            default:return noise(v)*((v.age%500)<170?1:.2f);
        }
    }
    private float genBraids(Voice v,double hz){
        int model=(int)(parameter(9,v)*47.99f);
        float h=parameter(17,v),t=parameter(25,v),m=parameter(33,v);
        int group=model/6;int sh=model%4;
        float x=phasor(v,4,hz*(1+(group==4?h:0)),sh);
        switch(group){
            case 0:return x*(1-m)+phasor(v,5,hz*1.003,2)*m;
            case 1:return (float)Math.sin(2*Math.PI*advance(v,5,hz,0)+4*t*Math.sin(2*Math.PI*advance(v,6,hz*(1+model%5),0)));
            case 2:return (float)Math.tanh((2+h*7)*x);
            case 3:return x*phasor(v,5,hz*(1+model%3),0);
            case 4:return (x+phasor(v,5,hz*1.5,sh)+phasor(v,6,hz*2,sh))*.32f;
            case 5:return genSpeech(v,hz)*(.5f+.5f*t);
            case 6:return noise(v)*(.25f+.75f*h)+x*.32f;
            default:return (float)Math.sin(2*Math.PI*advance(v,5,hz,0))*v.env+noise(v)*m*.3f;
        }
    }
    private float genDMG(Voice v,double hz){
        int mode=(int)(parameter(10,v)*3.99f);
        float duty=.125f+.375f*parameter(26,v);
        double p=advance(v,8,hz);
        float wave=p<duty?1:-1;
        switch(mode){
            case 1:return (float)(1-4*Math.abs(p-.5));
            case 2:return wave*.75f+phasor(v,9,hz*2,3)*.25f;
            case 3:return noise(v)*((v.age%7)<3?1:.3f);
            default:return wave;
        }
    }
    private float genSample(Voice v,double hz){
        float[] sample=sampleData;
        if(sample==null||sample.length<2)return 0;
        double speed=hz/440*sampleRate/(double)rate;
        float grain=parameter(P_GRAIN,v);
        float index=v.samplePosition;
        if(index>=sample.length-1){if(grain<=.05f)return 0;index=0;v.samplePosition=0;}
        if(index<0)index=0;
        int i=(int)index;
        float x=sample[i]+(sample[Math.min(sample.length-1,i+1)]-sample[i])*(index-i);
        v.samplePosition+=speed;
        if(grain>.05f){
            v.sampleGrainPhase+=(2+grain*30f)/rate;
            if(v.sampleGrainPhase>=1){
                v.sampleGrainPhase-=1;
                v.samplePosition=Math.max(0,Math.min(sample.length-2,sample.length*(parameter(P_SAMPLESTART,v)+noise(v)*grain*.3f)));
            }
        }
        return x;
    }
    private float genSpeech(Voice v,double hz){
        float h=parameter(20,v),t=parameter(28,v);
        double p=advance(v,12,hz);
        float source=(float)(2*p-1);
        float[] form1={800,500,330,470,570};
        float[] form2={1150,1700,2000,900,1200};
        int vowel=Math.min(4,(int)(h*5));
        double f1=form1[vowel],f2=form2[vowel];
        float a=(float)Math.sin(2*Math.PI*advance(v,13,f1,0));
        float b=(float)Math.sin(2*Math.PI*advance(v,14,f2,0));
        return source*(.35f+.65f*(a*a*.65f+b*b*.35f))+(float)Math.sin(2*Math.PI*p)*t*.3f;
    }
    private float genRings(Voice v,double hz){
        float damp=parameter(P_RINGDAMP,v),bright=parameter(29,v);
        int a=v.ringPtr,b=(a+1)%v.ringLength;
        float y=v.ring[a];
        float next=(v.ring[a]+v.ring[b])*.5f*(.9925f-damp*.025f);
        v.ring[a]=next;
        v.ringPtr=b;
        if(v.gate&&v.age%Math.max(1,(int)(rate/(hz*2)))==0)
            v.ring[a]+=noise(v)*.001f*bright;
        return y;
    }
    private float genHandpan(Voice v,double hz){
        int material=Math.min(7,(int)(parameter(P_MATERIAL,v)*8));
        float spread=parameter(30,v),hard=parameter(38,v);
        double[] base={1,2.01,2.99,4.02,5.18,6.2,7.4,8.5};
        float sum=0;
        for(int i=0;i<8;i++){
            float n=(float)(hz*base[i]*(1+material*.004f*i)*(1+spread*.02f));
            float coef=(float)Math.sin(2*Math.PI*Math.min(rate*.45,n)/rate);
            float radius=.99995f-.0001f*i-.00005f*material;
            float y=2*radius*(float)Math.cos(2*Math.PI*n/rate)*v.modal[i]-radius*radius*v.modalB[i];
            if(v.age<10)y+=noise(v)*.13f*hard*v.velocity;
            v.modalB[i]=v.modal[i];v.modal[i]=y;
            sum+=y/(1+i*.9f);
        }
        return sum*.17f;
    }
    private float genAir(Voice v,double hz){
        float pressure=parameter(P_BREATH,v),open=parameter(31,v),turb=parameter(39,v);
        float target=v.gate?pressure*(.7f+modWheel*.3f+aftertouch*.5f):0;
        v.airEnv+=(target-v.airEnv)*(v.gate?.0008f:.0004f);
        float returned=v.air[v.airPtr];
        v.airLp+=(returned-v.airLp)*(.1f+open*.55f);
        float breath=v.airEnv+noise(v)*turb*.025f;
        float reed=(float)Math.tanh((breath-v.airLp)*(.8f+2*open));
        float output=(reed-v.airDC)*.68f;
        v.airDC+=(reed-v.airDC)*.002f;
        v.air[v.airPtr]=(output+v.airLp*(.95f-.05f*turb))*(-1f);
        if(++v.airPtr>=v.airLength)v.airPtr=0;
        return output;
    }
    public synchronized void setSample(float[] samples,int sourceRate,String name){
        sampleData=samples;sampleRate=sourceRate;sampleName=name==null?"SAMPLE":name;
    }
    public synchronized void capture(int type,int a,int b){
        LoopSlot s=loops[activeBank][activeSlot];
        if(!s.recording&&!s.overdub)return;
        if(s.events.size()>=8192)return;
        long pos=s.recording?Math.max(0,frames-s.start):(frames-s.start)%Math.max(1,s.length);
        s.events.add(new LoopEvent(pos,type,a,b));
    }
    public synchronized void toggleRecord(){
        LoopSlot s=loops[activeBank][activeSlot];
        if(s.recording){
            s.recording=false;s.playing=!s.events.isEmpty();
            s.length=Math.max(rate/4,frames-s.start);
            s.start=frames;s.playIndex=0;s.previousPhase=-1;
            s.events.sort((a,b)->Long.compare(a.offset,b.offset));
        }else{
            s.events.clear();s.start=frames;s.recording=true;s.playing=false;s.playIndex=0;
        }
    }
    public synchronized void togglePlay(){
        LoopSlot s=loops[activeBank][activeSlot];
        if(s.playing){s.playing=false;allNotesOff();}
        else if(!s.events.isEmpty()){s.playing=true;s.start=frames;s.playIndex=0;s.previousPhase=-1;}
    }
    public synchronized void toggleOverdub(){
        LoopSlot s=loops[activeBank][activeSlot];
        s.overdub=!s.overdub;if(s.overdub){s.playing=true;s.recording=false;}
        if(!s.overdub)s.events.sort((a,b)->Long.compare(a.offset,b.offset));
    }
    public synchronized void clearLoop(){
        LoopSlot s=loops[activeBank][activeSlot];
        s.events.clear();s.recording=false;s.playing=false;s.overdub=false;s.playIndex=0;s.previousPhase=-1;
    }

    // Four capture/morph states following the desktop LOOM STATES workflow.
    public final float[][] states=new float[4][NUM];
    public final boolean[] stateCaptured=new boolean[4];
    public int activeState=0,stateA=0,stateB=1;
    public float stateMorph=0;
    public synchronized void captureState(int index){
        if(index<0||index>=4)return;
        System.arraycopy(values,0,states[index],0,NUM);
        stateCaptured[index]=true;activeState=index;
    }
    public synchronized void recallState(int index){
        if(index<0||index>=4||!stateCaptured[index])return;
        System.arraycopy(states[index],0,values,0,NUM);activeState=index;
    }
    public synchronized void setStateMorph(float amount){
        stateMorph=clamp(amount);
        if(!stateCaptured[stateA]||!stateCaptured[stateB])return;
        for(int i=0;i<NUM;i++){
            boolean discrete=i>=8&&i<16;
            values[i]=discrete?(stateMorph<.5f?states[stateA][i]:states[stateB][i]):
                states[stateA][i]+(states[stateB][i]-states[stateA][i])*stateMorph;
        }
    }

    public synchronized void saveTo(JSONObject o){
        try{
            JSONArray val=new JSONArray();for(float x:values)val.put(x);o.put("values",val);
            JSONArray sources=new JSONArray(),dests=new JSONArray(),depth=new JSONArray();
            for(int i=0;i<ROUTES;i++){sources.put(routeSource[i]);dests.put(routeDest[i]);depth.put(routeDepth[i]);}
            o.put("modSource",sources);o.put("modDest",dests);o.put("modDepth",depth);
            JSONArray ccs=new JSONArray();for(int x:ccMappings)ccs.put(x);o.put("ccMappings",ccs);
            JSONArray fx=new JSONArray(),order=new JSONArray();
            for(boolean x:fxEnabled)fx.put(x);for(int x:fxOrder)order.put(x);
            o.put("fxEnabled",fx);o.put("fxOrder",order);
            o.put("arp",arpEnabled);o.put("seq",seqEnabled);
            JSONArray seq=new JSONArray();for(int i=0;i<16;i++){
                JSONObject e=new JSONObject();e.put("on",stepEnabled[i]);e.put("note",stepNotes[i]);seq.put(e);
            }
            o.put("steps",seq);
            JSONArray stateArray=new JSONArray(),captured=new JSONArray();
            for(int q=0;q<4;q++){JSONArray row=new JSONArray();for(float x:states[q])row.put(x);stateArray.put(row);captured.put(stateCaptured[q]);}
            o.put("states",stateArray);o.put("captured",captured);o.put("stateA",stateA);o.put("stateB",stateB);o.put("stateMorph",stateMorph);
        }catch(Exception ignored){}
    }
    public synchronized void loadFrom(JSONObject o){
        try{
            JSONArray a=o.optJSONArray("values");
            if(a!=null)for(int i=0;i<Math.min(a.length(),NUM);i++)values[i]=clamp((float)a.getDouble(i));
            a=o.optJSONArray("modSource");if(a!=null)for(int i=0;i<Math.min(ROUTES,a.length());i++)routeSource[i]=a.getInt(i);
            a=o.optJSONArray("modDest");if(a!=null)for(int i=0;i<Math.min(ROUTES,a.length());i++)routeDest[i]=a.getInt(i);
            a=o.optJSONArray("modDepth");if(a!=null)for(int i=0;i<Math.min(ROUTES,a.length());i++)routeDepth[i]=(float)a.getDouble(i);
            a=o.optJSONArray("ccMappings");if(a!=null)for(int i=0;i<Math.min(128,a.length());i++)ccMappings[i]=a.getInt(i);
            a=o.optJSONArray("fxEnabled");if(a!=null)for(int i=0;i<Math.min(5,a.length());i++)fxEnabled[i]=a.getBoolean(i);
            a=o.optJSONArray("fxOrder");if(a!=null)for(int i=0;i<Math.min(5,a.length());i++)fxOrder[i]=a.getInt(i);
            arpEnabled=o.optBoolean("arp",false);seqEnabled=o.optBoolean("seq",false);
            a=o.optJSONArray("states");if(a!=null)for(int q=0;q<Math.min(4,a.length());q++){
                JSONArray row=a.getJSONArray(q);for(int j=0;j<Math.min(NUM,row.length());j++)states[q][j]=clamp((float)row.getDouble(j));
            }
            a=o.optJSONArray("captured");if(a!=null)for(int q=0;q<Math.min(4,a.length());q++)stateCaptured[q]=a.getBoolean(q);
            stateA=Math.max(0,Math.min(3,o.optInt("stateA",0)));stateB=Math.max(0,Math.min(3,o.optInt("stateB",1)));
            stateMorph=clamp((float)o.optDouble("stateMorph",0));
            a=o.optJSONArray("steps");if(a!=null)for(int i=0;i<Math.min(16,a.length());i++){
                JSONObject s=a.getJSONObject(i);stepEnabled[i]=s.optBoolean("on");stepNotes[i]=s.optInt("note",60);
            }
        }catch(Exception ignored){}
    }
    public synchronized float meterMobile(){return vu;}
    @Override public synchronized int voiceCount(){
        int n=0;for(Voice v:voices)if(v.active)n++;return n;
    }
    private void updateMod(){
        System.arraycopy(values,0,modulated,0,NUM);
        float l1=(float)Math.sin(lfoPhase1*Math.PI*2),l2=(float)Math.sin(lfoPhase2*Math.PI*2);
        float[] src={l1,l2,0,0,0,modWheel,pitchBend,xyX*2-1,xyY*2-1,0,randomValue,aftertouch};
        float velo=0,env=0,env2=0;
        for(Voice v:voices)if(v.active){velo=Math.max(velo,v.velocity);env=Math.max(env,v.env);env2=Math.max(env2,v.env2);}
        src[2]=env2*2-1;src[3]=env*2-1;src[4]=velo*2-1;
        src[9]=(sequenceStep/7.5f)-1f;
        for(int i=0;i<ROUTES;i++){
            int s=routeSource[i],d=routeDest[i];
            if(s>=0&&s<src.length&&d>=0&&d<NUM)
                modulated[d]=clamp(modulated[d]+src[s]*routeDepth[i]*.5f);
        }
    }
    private void sequencerTick(){
        long interval=(long)(rate*60.0/(55+values[P_TEMPO]*185)/4);
        if(interval<1)interval=1;
        if(seqEnabled&&frames-stepClock>=interval){
            stepClock=frames;
            int index=sequenceStep;sequenceStep=(sequenceStep+1)%16;
            if(playingNote>=0){for(Voice v:voices)if(v.active&&v.note==playingNote)v.gate=false;playingNote=-1;}
            if(stepEnabled[index]){playingNote=stepNotes[index];trigger(playingNote,100);}
        }
        if(arpEnabled&&frames-arpClock>=interval){
            arpClock=frames;
            if(playingNote>=0){for(Voice v:voices)if(v.active&&v.note==playingNote)v.gate=false;playingNote=-1;}
            int first=heldNote;
            if(first<0)for(int n=0;n<128;n++)if(noteHeld[n]){first=n;break;}
            if(first>=0){playingNote=Math.min(127,first+12*(arpTick++%3));trigger(playingNote,sampleVelocity[first]);}
        }
    }
    private void playLoops(){
        for(int b=0;b<8;b++)for(int s=0;s<8;s++){
            LoopSlot slot=loops[b][s];
            if(!slot.playing||slot.events.isEmpty()||slot.recording)continue;
            long t=(frames-slot.start)%Math.max(1,slot.length);
            if(t<0)continue;
            if(slot.previousPhase>=0&&t<slot.previousPhase){slot.playIndex=0;}
            slot.previousPhase=t;
            while(slot.playIndex<slot.events.size()&&slot.events.get(slot.playIndex).offset<=t){
                LoopEvent e=slot.events.get(slot.playIndex++);
                if((e.status&0xf0)==0x90&&e.b>0)trigger(e.a,e.b);
                else if((e.status&0xf0)==0x80||((e.status&0xf0)==0x90&&e.b==0))
                    for(Voice v:voices)if(v.active&&v.note==e.a)v.gate=false;
                else if((e.status&0xf0)==0xb0&&e.a==1)modWheel=e.b/127f;
            }
        }
    }
    @Override public synchronized void render(short[] out,int count){
        float peak=0;
        int delayTime=Math.max(128,(int)(rate*(.05+values[P_DELAY]*.6)));
        for(int k=0;k<count;k++){
            if((frames&63)==0){
                lfoPhase1+=(float)(64*(.05+values[P_LFO1]*18)/rate);lfoPhase1-=Math.floor(lfoPhase1);
                lfoPhase2+=(float)(64*(.05+values[P_LFO2]*18)/rate);lfoPhase2-=Math.floor(lfoPhase2);
                updateMod();
                sequencerTick();
                playLoops();
            }
            float dry=0;
            float af=(float)(1-Math.exp(-1.0/(rate*(.001+modulated[P_ATTACK]*modulated[P_ATTACK]*2))));
            float df=(float)(1-Math.exp(-1.0/(rate*(.005+modulated[P_DECAY]*2))));
            float rf=(float)(1-Math.exp(-1.0/(rate*(.005+modulated[P_RELEASE]*3))));
            float filterHz=(float)(45*Math.pow(310,modulated[P_FILTER]));
            float f=(float)(1-Math.exp(-2*Math.PI*Math.min(filterHz,rate*.40)/rate));
            for(Voice v:voices){
                if(!v.active)continue;
                v.age++;
                if(v.gate){
                    if(v.env<.99f)v.env+=(1-v.env)*af;else v.env+=(modulated[P_SUSTAIN]-v.env)*df;
                    v.env2+=(1-v.env2)*.0008f;
                }else{
                    v.env*=1-rf;
                    v.env2*=.9994f;
                    if(v.env<.0001f){v.active=false;continue;}
                }
                double hz=freq(v);
                float voice=0,excitation=0;
                for(int e=0;e<ENGINE_COUNT;e++){
                    float level=parameter(e,v);
                    if(level<.0005f)continue;
                    float value=0;
                    switch(e){
                        case 0:value=genPlaits(v,hz);break;
                        case 1:value=genBraids(v,hz);break;
                        case 2:value=genDMG(v,hz);break;
                        case 3:value=genSample(v,hz);break;
                        case 4:value=genSpeech(v,hz);break;
                        case 5:value=genRings(v,hz);break;
                        case 6:value=genHandpan(v,hz);break;
                        case 7:value=genAir(v,hz);break;
                    }
                    if(e==5||e==7)value+=excitation*.08f;
                    excitation+=value*level;
                    voice+=value*level*.38f;
                }
                float q=modulated[P_RESO]*.87f;
                v.band+=(voice-v.low-v.band*q)*f;
                v.low+=v.band*f;
                dry+=v.low*v.env*v.velocity*.55f;
            }
            float left=dry,right=dry;
            for(int slot=0;slot<fxOrder.length;slot++){
                int fx=fxOrder[slot];if(!fxEnabled[fx])continue;
                switch(fx){
                    case 0:
                        left=(float)Math.tanh(left*(1+modulated[P_DRIVE]*12));
                        right=(float)Math.tanh(right*(1+modulated[P_DRIVE]*12));
                        break;
                    case 1:
                        float crush=(float)Math.pow(2,4+12*(1-modulated[P_CRUSH]));
                        left=Math.round(left*crush)/crush;right=Math.round(right*crush)/crush;break;
                    case 2:
                        float modulation=(float)Math.sin(lfoPhase1*2*Math.PI)*modulated[P_CHORUS];
                        float a=modulated[P_CHORUS]*.3f;
                        left+=Math.sin(vu*3+frames*.00011)*a+right*modulation*.12f;
                        right+=Math.cos(vu*3+frames*.00012)*a+left*modulation*.12f;break;
                    case 3:
                        int delayed=delayPtr-delayTime;if(delayed<0)delayed+=delayL.length;
                        float tapL=delayL[delayed],tapR=delayR[delayed];
                        delayL[delayPtr]=left+tapR*.45f;
                        delayR[delayPtr]=right+tapL*.45f;
                        left+=tapL*modulated[P_DELAY]*.5f;
                        right+=tapR*modulated[P_DELAY]*.5f;
                        if(++delayPtr==delayL.length)delayPtr=0;
                        break;
                    case 4:
                        int ri=revPtr%revL.length,rj=revPtr%revR.length;
                        float tailL=revL[ri],tailR=revR[rj];
                        revL[ri]=left*.15f+tailL*.85f+tailR*.08f;
                        revR[rj]=right*.15f+tailR*.85f+tailL*.08f;
                        left+=tailL*modulated[P_REVERB]*.7f;
                        right+=tailR*modulated[P_REVERB]*.7f;
                        if(++revPtr>=Math.max(revL.length,revR.length))revPtr=0;
                        break;
                }
            }
            float m=modulated[P_MASTER];
            left=(float)Math.tanh(left*m);
            right=(float)Math.tanh(right*m);
            peak=Math.max(peak,Math.max(Math.abs(left),Math.abs(right)));
            out[k*2]=(short)(Math.max(-1,Math.min(1,left))*32767);
            out[k*2+1]=(short)(Math.max(-1,Math.min(1,right))*32767);
            frames++;
        }
        vu=vu*.7f+peak*.3f;
    }
}
