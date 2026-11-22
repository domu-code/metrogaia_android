package com.domucode.synth;

/** Portable Java audio kernel. Experimental Android algorithms are not claimed
 * to replace MetroGaia's original Beads DSP or LOOM's source models.
 */
public final class SynthEngine {
    public static final int SLOTS=4, MAX_VOICES=12;
    public static final String[] WAVES={"SINE","SAW","TRI","PULSE","WAVE","DMG","FM","NOISE","RESO"};
    public final int[] waves={4,1,2,5};
    public final float[] levels={.45f,.24f,.18f,.13f};
    public final float[] cents={0,0,0,0};
    public float cutoff=.75f,resonance=.2f,attack=.02f,decay=.25f,sustain=.65f,release=.3f;
    public float delay=.17f,feedback=.25f,drive=.1f,master=.7f,bend=0f,modWheel=0f;
    private final int rate;
    private final Voice[] voices=new Voice[MAX_VOICES];
    private final float[] echoL,echoR;
    private int echoIndex=0,heldSustain=0;
    private long voiceClock=0;
    private float vu=0f;
    private static final class Voice {
        boolean active,gate,pedal;
        int note;
        float velocity,env,f1,f2;
        final double[] phase=new double[SLOTS];
        long stamp;
    }
    public SynthEngine(int sampleRate) {
        rate=sampleRate;
        for(int i=0;i<voices.length;i++)voices[i]=new Voice();
        echoL=new float[rate];echoR=new float[rate];
    }
    public synchronized void noteOn(int note,int velocity) {
        if(note<0||note>127||velocity<=0)return;
        Voice v=null;
        for(Voice one:voices)if(one.active&&one.note==note){v=one;break;}
        if(v==null)for(Voice one:voices)if(!one.active){v=one;break;}
        if(v==null){v=voices[0];for(Voice one:voices)if(one.stamp<v.stamp)v=one;}
        v.active=true;v.gate=true;v.pedal=false;v.note=note;v.velocity=velocity/127f;
        v.env=0;v.f1=0;v.f2=0;v.stamp=++voiceClock;
        for(int j=0;j<SLOTS;j++)v.phase[j]=0;
    }
    public synchronized void noteOff(int note){
        for(Voice v:voices)if(v.active&&v.note==note){
            if(heldSustain!=0)v.pedal=true;else v.gate=false;
        }
    }
    public synchronized void allNotesOff(){
        heldSustain=0;for(Voice v:voices){v.gate=false;v.pedal=false;}
    }
    public synchronized void cc(int cc,int value){
        float n=Math.max(0,Math.min(127,value))/127f;
        switch(cc){
            case 1:modWheel=n;break;
            case 7:master=n;break;
            case 64:heldSustain=value>=64?1:0;
                if(heldSustain==0)for(Voice v:voices)if(v.pedal){v.pedal=false;v.gate=false;}
                break;
            case 71:resonance=n;break;
            case 72:release=.01f+n*n*2.5f;break;
            case 73:attack=.001f+n*n*2f;break;
            case 74:cutoff=n;break;
            case 91:delay=n*.65f;break;
            case 120:case 123:allNotesOff();break;
            default:break;
        }
    }
    public synchronized void pitchBend(int bend14){bend=(bend14-8192)/8192f;}
    public synchronized int voiceCount(){
        int n=0;for(Voice v:voices)if(v.active)n++;return n;
    }
    public synchronized float meter(){return vu;}
    private float coeff(float seconds){
        return (float)(1-Math.exp(-1.0/(Math.max(.0001f,seconds)*rate)));
    }
    public synchronized void render(short[] out,int frames){
        final double tau=2*Math.PI,sr=rate;
        final float ac=coeff(.001f+attack*attack*4),dc=coeff(.002f+decay*decay*4),rc=coeff(.002f+release*release*5);
        final float hz=(float)(70*Math.pow(180,Math.min(1,cutoff)));
        final float lp=(float)Math.exp(-tau*Math.min(hz,rate*.43)/sr),res=Math.min(.9f,resonance*.85f);
        final int delaySamples=Math.max(1,Math.min(rate-1,(int)(rate*(.09+.51*delay))));
        float peak=0;
        for(int f=0;f<frames;f++){
            float mono=0;
            for(Voice v:voices){
                if(!v.active)continue;
                if(v.gate){
                    if(v.env<.995f)v.env+=(1-v.env)*ac;
                    else v.env+=(sustain-v.env)*dc;
                }else{
                    v.env*=1-rc;
                    if(v.env<.0003f){v.active=false;continue;}
                }
                double base=440.0*Math.pow(2.0,(v.note-69+bend*2)/12.0);
                float mixed=0;
                for(int j=0;j<SLOTS;j++){
                    float level=levels[j];if(level<.0001f)continue;
                    double freq=base*Math.pow(2,cents[j]/1200.0);
                    double p=v.phase[j]+freq/sr;
                    p-=Math.floor(p);v.phase[j]=p;
                    double x;
                    switch(waves[j]){
                        case 0:x=Math.sin(tau*p);break;
                        case 1:x=2*p-1;break;
                        case 2:x=1-4*Math.abs(p-.5);break;
                        case 3:x=p<.5?1:-1;break;
                        case 4:x=Math.sin(tau*p)+.33*Math.sin(3*tau*p+modWheel*2);break;
                        case 5:x=p<(.125+.375*modWheel)?1:-1;break;
                        case 6:x=Math.sin(tau*p+2.8*Math.sin(tau*p*2));break;
                        case 7:x=(Math.random()*2-1)*.65;break;
                        case 8:x=Math.sin(tau*p)*Math.exp(-3*p);break;
                        default:x=0;
                    }
                    mixed+=(float)x*level;
                }
                mixed*=v.env*v.velocity*.5f;
                v.f1=v.f1*lp+mixed*(1-lp);
                v.f2=v.f2*lp+(v.f1-res*v.f2)*(1-lp);
                mono+=v.f2;
            }
            float dry=(float)Math.tanh(mono*(1+drive*7));
            int old=echoIndex-delaySamples;if(old<0)old+=rate;
            float el=echoL[old],er=echoR[old];
            float left=dry+el*.38f,right=dry+er*.38f;
            echoL[echoIndex]=dry+el*feedback*.85f;
            echoR[echoIndex]=dry+er*feedback*.85f;
            if(++echoIndex>=rate)echoIndex=0;
            left=(float)Math.tanh(left*master*.9f);
            right=(float)Math.tanh(right*master*.9f);
            peak=Math.max(peak,Math.max(Math.abs(left),Math.abs(right)));
            out[f*2]=(short)Math.max(-32767,Math.min(32767,(int)(left*32767)));
            out[f*2+1]=(short)Math.max(-32767,Math.min(32767,(int)(right*32767)));
        }
        vu=vu*.8f+peak*.2f;
    }
    public synchronized void preset(int n,boolean loom){
        int p=((n%8)+8)%8;
        int[][] metro={{4,1,2,5},{1,1,0,3},{5,3,5,0},{0,4,2,8},{6,4,6,0},{7,5,1,0},{8,0,8,2},{2,1,4,7}};
        int[][] lo={{4,1,5,8},{6,4,2,0},{5,5,0,1},{8,0,8,8},{8,6,0,2},{7,4,1,0},{4,4,2,0},{0,8,6,5}};
        float[] defaults={.45f,.22f,.18f,.13f};
        for(int i=0;i<4;i++){
            waves[i]=(loom?lo:metro)[p][i];levels[i]=defaults[i];cents[i]=0;
        }
        cutoff=new float[]{.83f,.58f,.75f,.61f,.92f,.42f,.7f,.8f}[p];
        attack=(p==3||p==6)?.65f:.025f;decay=.24f;sustain=p==6?.8f:.65f;
        release=(p==3||p==6)?.65f:.26f;
        delay=.15f;feedback=.25f;drive=.07f;master=.7f;
    }
}
