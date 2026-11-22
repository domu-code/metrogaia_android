package com.domucode.synth;

import android.content.Context;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Android MIDI output ports stream MIDI events to the synth. */
public final class AndroidMidi {
    private final MidiManager manager;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final SynthEngine synth;
    private MidiDevice device;
    private MidiOutputPort port;
    private final List<Choice> choices=new ArrayList<>();
    private int selection=0,generation=0;
    private static final class Choice {
        final MidiDeviceInfo info;final int port;final String label;
        Choice(MidiDeviceInfo i,int p,String s){info=i;port=p;label=s;}
    }
    public AndroidMidi(Context context,SynthEngine engine){
        synth=engine;
        manager=(MidiManager)context.getSystemService(Context.MIDI_SERVICE);
        refresh();
    }
    public static void accept(SynthEngine engine,byte[] msg,int offset,int count){
        int i=offset,end=offset+count;
        while(i<end){
            int status=msg[i++]&255;
            if(status<128||status>=240)continue;
            int type=status&240;
            int countData=(type==192||type==208)?1:2;
            if(i+countData>end)break;
            int a=msg[i++]&127,b=countData==2?msg[i++]&127:0;
            switch(type){
                case 128:engine.noteOff(a);break;
                case 144:if(b==0)engine.noteOff(a);else engine.noteOn(a,b);break;
                case 176:engine.cc(a,b);break;
                case 224:engine.pitchBend(a+(b<<7));break;
                default:break;
            }
        }
    }
    public void refresh(){
        close();
        choices.clear();choices.add(new Choice(null,-1,"TOUCH / VIRTUAL"));
        if(manager!=null)for(MidiDeviceInfo info:manager.getDevices()){
            String name=info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);
            if(name==null)name="MIDI "+info.getId();
            for(MidiDeviceInfo.PortInfo p:info.getPorts())
                if(p.getType()==MidiDeviceInfo.PortInfo.TYPE_OUTPUT)
                    choices.add(new Choice(info,p.getPortNumber(),name+" #"+p.getPortNumber()));
        }
        if(selection>=choices.size())selection=0;
        open();
    }
    public String label(){return choices.get(selection).label;}
    public void next(){
        if(choices.size()<2)return;
        selection=(selection+1)%choices.size();open();
    }
    private void open(){
        close();
        Choice c=choices.get(selection);
        if(c.info==null||manager==null)return;
        final int serial=generation;
        manager.openDevice(c.info,opened->{
            if(serial!=generation){
                try{if(opened!=null)opened.close();}catch(IOException ignored){}
                return;
            }
            if(opened==null)return;
            device=opened;port=opened.openOutputPort(c.port);
            if(port!=null)port.connect(new MidiReceiver(){
                @Override public void onSend(byte[] msg,int off,int n,long timestamp)throws IOException{
                    accept(synth,msg,off,n);
                }
            });
        },handler);
    }
    public void close(){
        generation++;
        try{if(port!=null)port.close();}catch(IOException ignored){}
        try{if(device!=null)device.close();}catch(IOException ignored){}
        port=null;device=null;
    }
}
