package com.domucode.synth;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** LOOM mobile front end and file-based patch/loop persistence. */
public class LoomActivity extends Activity {
    private static final int PICK_WAV=711;
    private SynthRuntime runtime;
    private LoomEngine engine;
    private AndroidMidi midi;
    private LoomView view;
    private SharedPreferences presets;
    private int currentPatch=0;
    private volatile String status="READY";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN|
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        runtime=SynthRuntime.get(this);
        engine=(LoomEngine)runtime.engine;
        runtime.acquire();
        presets=getSharedPreferences("loom-patches-v2",MODE_PRIVATE);
        midi=new AndroidMidi(this,engine);
        restoreLoops();
        restorePatch(0);
        view=new LoomView(this,engine,midi,new LoomView.Host(){
            @Override public void save(){savePatch();}
            @Override public void load(){restorePatch(currentPatch);}
            @Override public void next(){currentPatch=(currentPatch+1)%8;restorePatch(currentPatch);}
            @Override public void previous(){currentPatch=(currentPatch+7)%8;restorePatch(currentPatch);}
            @Override public int patch(){return currentPatch;}
            @Override public void select(int index){currentPatch=Math.max(0,Math.min(7,index));restorePatch(currentPatch);}
            @Override public void loadWav(){chooseSample();}
            @Override public void loopsChanged(){saveLoops();}
            @Override public String status(){return status;}
        });
        setContentView(view);
    }
    private void savePatch(){
        try{
            JSONObject o=new JSONObject();
            engine.saveTo(o);
            presets.edit().putString("slot-"+currentPatch,o.toString()).apply();
            status="SAVED PATCH "+(currentPatch+1);
        }catch(Exception ex){status="SAVE ERROR";}
        view.invalidate();
    }
    private void restorePatch(int index){
        engine.presetMobile(index);
        String raw=presets.getString("slot-"+index,null);
        if(raw!=null)try{engine.loadFrom(new JSONObject(raw));}catch(Exception ignored){}
        status="PATCH "+(index+1)+" LOADED";
        if(view!=null)view.invalidate();
    }
    private void chooseSample(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/wav","audio/x-wav","audio/wave","audio/vnd.wave","application/octet-stream"});
        startActivityForResult(intent,PICK_WAV);
    }
    private static int u16(byte[] b,int x){
        return (b[x]&255)|((b[x+1]&255)<<8);
    }
    private static int i32(byte[] b,int x){
        return (b[x]&255)|((b[x+1]&255)<<8)|((b[x+2]&255)<<16)|((b[x+3]&255)<<24);
    }
    private static String chunk(byte[] b,int i){
        return new String(b,i,4,StandardCharsets.US_ASCII);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_WAV||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        status="LOADING WAV";
        final String sampleName=uri.getLastPathSegment()==null?"WAV":uri.getLastPathSegment();
        new Thread(()->{
            try(InputStream stream=getContentResolver().openInputStream(uri)){
                if(stream==null)throw new Exception("NO INPUT");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                byte[] block=new byte[8192];
                int read;int total=0;
                while((read=stream.read(block))>=0){
                    total+=read;
                    if(total>24*1024*1024)throw new Exception("WAV TOO LARGE");
                    bytes.write(block,0,read);
                }
                byte[] b=bytes.toByteArray();
                if(b.length<44||!chunk(b,0).equals("RIFF")||!chunk(b,8).equals("WAVE"))
                    throw new Exception("WAV PCM ONLY");
                int format=0,channels=0,sr=0,bits=0,start=-1,length=0;
                for(int p=12;p+8<=b.length;){
                    int size=i32(b,p+4);
                    if(size<0||size>b.length-p-8)break;
                    String id=chunk(b,p);
                    if(id.equals("fmt ")&&size>=16){
                        format=u16(b,p+8);channels=u16(b,p+10);sr=i32(b,p+12);bits=u16(b,p+22);
                    }
                    if(id.equals("data")){start=p+8;length=size;break;}
                    p+=8+size+(size&1);
                }
                if(start<0||channels<1||channels>2||sr<8000||sr>192000
                    ||!((format==1&&bits==16)||(format==3&&bits==32)))
                    throw new Exception("USE PCM16 OR FLOAT32 WAV");
                int bytesPerSample=bits/8,frames=length/(channels*bytesPerSample);
                if(frames<2)throw new Exception("EMPTY WAV");
                float[] decoded=new float[frames];
                for(int i=0;i<frames;i++){
                    float mixed=0;
                    for(int ch=0;ch<channels;ch++){
                        int offset=start+(i*channels+ch)*bytesPerSample;
                        if(format==1)mixed+=(short)u16(b,offset)/32768f;
                        else mixed+=Float.intBitsToFloat(i32(b,offset));
                    }
                    decoded[i]=Math.max(-1,Math.min(1,mixed/channels));
                }
                engine.setSample(decoded,sr,sampleName);
                status="WAV LOADED "+(frames/(float)sr)+" SEC";
            }catch(Exception e){status="SAMPLE ERROR "+e.getMessage();}
            runOnUiThread(()->{if(view!=null)view.invalidate();});
        },"LoomWavLoader").start();
    }
    private void saveLoops(){
        try{
            JSONArray root=new JSONArray();
            synchronized(engine){
                for(int b=0;b<8;b++){
                    JSONArray bank=new JSONArray();
                    for(int s=0;s<8;s++){
                        LoomEngine.LoopSlot slot=engine.loops[b][s];
                        JSONObject o=new JSONObject();o.put("length",slot.length);o.put("bars",slot.bars);
                        JSONArray ev=new JSONArray();
                        for(LoomEngine.LoopEvent e:slot.events){
                            JSONArray a=new JSONArray();a.put(e.offset);a.put(e.status);a.put(e.a);a.put(e.b);
                            ev.put(a);
                        }
                        o.put("events",ev);bank.put(o);
                    }
                    root.put(bank);
                }
            }
            try(FileOutputStream out=openFileOutput("loom-midi-loops.json",MODE_PRIVATE)){
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
            status="LOOP BANKS SAVED";
        }catch(Exception e){status="LOOP SAVE ERROR";}
    }
    private void restoreLoops(){
        try(FileInputStream in=openFileInput("loom-midi-loops.json")){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            byte[] block=new byte[4096];int n;
            while((n=in.read(block))>0){
                if(bytes.size()+n>20*1024*1024)throw new Exception("Loop file too large");
                bytes.write(block,0,n);
            }
            JSONArray root=new JSONArray(bytes.toString("UTF-8"));
            synchronized(engine){
                for(int b=0;b<Math.min(8,root.length());b++){
                    JSONArray bank=root.getJSONArray(b);
                    for(int s=0;s<Math.min(8,bank.length());s++){
                        LoomEngine.LoopSlot slot=engine.loops[b][s];
                        JSONObject o=bank.getJSONObject(s);
                        slot.length=Math.max(1,o.optLong("length",1));slot.bars=o.optInt("bars",1);
                        JSONArray arr=o.optJSONArray("events");
                        if(arr==null)continue;
                        slot.events.clear();
                        for(int i=0;i<Math.min(8192,arr.length());i++){
                            JSONArray e=arr.getJSONArray(i);
                            slot.events.add(new LoomEngine.LoopEvent(e.getLong(0),e.getInt(1),e.getInt(2),e.getInt(3)));
                        }
                    }
                }
            }
        }catch(Exception ignored){}
    }
    @Override protected void onPause(){
        super.onPause();
        if(view!=null)view.releaseKeys();
    }
    @Override protected void onDestroy(){
        if(view!=null)view.releaseKeys();
        if(midi!=null)midi.close();
        if(runtime!=null)runtime.release();
        super.onDestroy();
    }
}
