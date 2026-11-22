package com.domucode.synth;

import android.media.midi.MidiDeviceService;
import android.media.midi.MidiReceiver;
import java.io.IOException;

/** Virtual MIDI IN destination for other Android MIDI applications. */
public final class SynthMidiService extends MidiDeviceService {
    private SynthRuntime runtime;
    private final MidiReceiver[] receivers={new MidiReceiver(){
        @Override public void onSend(byte[] bytes,int offset,int count,long timestamp)throws IOException{
            AndroidMidi.accept(SynthRuntime.get().engine,bytes,offset,count);
        }
    }};
    @Override public void onCreate(){super.onCreate();runtime=SynthRuntime.get();runtime.acquire();}
    @Override public MidiReceiver[] onGetInputPortReceivers(){return receivers;}
    @Override public void onDestroy(){if(runtime!=null)runtime.release();super.onDestroy();}
}
