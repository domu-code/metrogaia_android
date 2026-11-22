package com.domucode.synth;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Process;

/** AudioTrack renderer shared between an activity and the virtual MIDI service. */
public final class SynthRuntime {
    private static SynthRuntime instance;
    public final SynthEngine engine;
    private final int rate;
    private volatile boolean running=false;
    private AudioTrack track;
    private Thread thread;
    private int clients;
    private SynthRuntime(boolean loom){
        int suggested=AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        rate=suggested>=22050&&suggested<=192000?suggested:48000;
        engine=loom?new LoomEngine(rate):new SynthEngine(rate);
    }
    public static synchronized SynthRuntime get(Context context){
        if(instance==null)instance=new SynthRuntime(context.getPackageName().equals("com.domucode.loom"));
        return instance;
    }
    public static synchronized SynthRuntime get(){
        if(instance==null)throw new IllegalStateException("SynthRuntime requires Android Context for initialization");
        return instance;
    }
    public synchronized void acquire(){
        if(++clients>1)return;
        int min=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
        if(min<1)min=8192;
        track=new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(Math.max(min*2,4096))
            .setTransferMode(AudioTrack.MODE_STREAM).build();
        if(track.getState()!=AudioTrack.STATE_INITIALIZED){track.release();track=null;return;}
        running=true;track.play();
        thread=new Thread(()->{
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            short[] buf=new short[512];
            while(running){
                engine.render(buf,256);
                int i=0;
                while(running&&i<buf.length){
                    try{
                        int done=track.write(buf,i,buf.length-i,AudioTrack.WRITE_BLOCKING);
                        if(done<=0)break;
                        i+=done;
                    }catch(IllegalStateException ex){return;}
                }
            }
        },"SynthAudio");
        thread.setDaemon(true);thread.start();
    }
    public synchronized void release(){
        clients=Math.max(0,clients-1);if(clients>0)return;
        running=false;
        if(track!=null){track.pause();track.flush();}
        if(thread!=null){
            try{thread.join(500);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        }
        if(track!=null){track.release();track=null;}
        thread=null;engine.allNotesOff();
    }
    public synchronized boolean isRunning(){return running;}
}
