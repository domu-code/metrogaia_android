package com.domucode.synth;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import org.json.JSONArray;
import org.json.JSONObject;

/** Android entry point shared by the two independently installable apps. */
public abstract class SynthActivity extends Activity {
    private SynthRuntime runtime;
    private AndroidMidi midi;
    private SynthView view;
    private SharedPreferences patches;
    private int currentPatch=0;
    protected abstract boolean isLoom();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN|
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        runtime=SynthRuntime.get();
        runtime.acquire();
        patches=getSharedPreferences("mobile-synth-patches",MODE_PRIVATE);
        midi=new AndroidMidi(this,runtime.engine);
        load(0);
        view=new SynthView(this,runtime.engine,midi,isLoom(),new SynthView.PatchHandler(){
            @Override public void save(){SynthActivity.this.save();}
            @Override public void load(){SynthActivity.this.load(currentPatch);}
            @Override public void next(){currentPatch=(currentPatch+1)%8;load(currentPatch);}
            @Override public void previous(){currentPatch=(currentPatch+7)%8;load(currentPatch);}
            @Override public int index(){return currentPatch;}
        });
        setContentView(view);
    }

    private void save() {
        try {
            SynthEngine e=runtime.engine;
            JSONObject o=new JSONObject();
            JSONArray wave=new JSONArray(),level=new JSONArray(),cents=new JSONArray();
            for(int i=0;i<4;i++){wave.put(e.waves[i]);level.put(e.levels[i]);cents.put(e.cents[i]);}
            o.put("wave",wave);o.put("level",level);o.put("cents",cents);
            o.put("cutoff",e.cutoff);o.put("resonance",e.resonance);
            o.put("attack",e.attack);o.put("decay",e.decay);o.put("sustain",e.sustain);o.put("release",e.release);
            o.put("delay",e.delay);o.put("feedback",e.feedback);o.put("drive",e.drive);o.put("master",e.master);
            patches.edit().putString("patch-"+currentPatch,o.toString()).apply();
        } catch(Exception ignored) {}
    }
    private void load(int index){
        SynthEngine e=runtime.engine;
        e.allNotesOff();
        e.preset(index,isLoom());
        String data=patches.getString("patch-"+index,null);
        if(data==null)return;
        try{
            JSONObject o=new JSONObject(data);
            JSONArray wave=o.getJSONArray("wave"),level=o.getJSONArray("level"),cents=o.getJSONArray("cents");
            for(int i=0;i<4;i++){
                e.waves[i]=Math.max(0,Math.min(SynthEngine.WAVES.length-1,wave.getInt(i)));
                e.levels[i]=(float)level.getDouble(i);
                e.cents[i]=(float)cents.getDouble(i);
            }
            e.cutoff=(float)o.getDouble("cutoff");e.resonance=(float)o.getDouble("resonance");
            e.attack=(float)o.getDouble("attack");e.decay=(float)o.getDouble("decay");
            e.sustain=(float)o.getDouble("sustain");e.release=(float)o.getDouble("release");
            e.delay=(float)o.getDouble("delay");e.feedback=(float)o.getDouble("feedback");
            e.drive=(float)o.getDouble("drive");e.master=(float)o.getDouble("master");
        }catch(Exception ignored){}
    }
    @Override protected void onDestroy(){
        if(view!=null)view.releaseKeys();
        if(midi!=null)midi.close();
        if(runtime!=null)runtime.release();
        super.onDestroy();
    }
}
