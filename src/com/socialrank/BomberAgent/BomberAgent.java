package com.socialrank.BomberAgent;

import java.io.IOException;
import java.util.HashMap;

import com.socialrank.BomberAgent.R;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.ScaleAnimation;
import android.widget.TextView;
import android.widget.VideoView;

public class BomberAgent extends Activity implements SensorEventListener {
   
	
	int state = R.layout.main;
	
	SensorManager SM;
	Handler H;
	double score = 0;
	
	Vibrator vibrator = null;
	PowerManager.WakeLock awaker = null;	
	HashMap<Integer,MediaPlayer> MPS = new HashMap<Integer,MediaPlayer>();
	HashMap<Sensor,SensorEvent> sensors = new HashMap<Sensor,SensorEvent>();
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        SM = (SensorManager) getSystemService(SENSOR_SERVICE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
    
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        awaker = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,"GAME");
        vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        H = new Handler();
        
        setContentView(state);
    }
    private Runnable runFlightOver = new Runnable() {
    	public void run() {
    		flightOver();
    	}
    };
    
    void flightOver(){
    	enterState(R.layout.congrats);
    }
    @Override
    protected void onPause() {
    	
    	super.onPause();
    	SM.unregisterListener(this);
    	sensors.clear();
    	awaker.release();
    	vibrator.cancel();
    	
    	for(MediaPlayer mp : MPS.values())
    		mp.release();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        awaker.acquire();
        
        Sensor sensor = SM.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensors.clear();
        sensors.put(sensor, null);
        SM.registerListener(this, 
        		sensor,
        		SensorManager.SENSOR_DELAY_FASTEST);
        
        
        int[]sounds = {R.raw.main,R.raw.congrats,R.raw.flying,R.raw.fail};
        for(int sound : sounds)
        	MPS.put(sound,MediaPlayer.create(this, sound));
        
        
        startClip();
    }
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    
    public void onSensorChanged(SensorEvent event) {
    	float[]values = event.values;
    	
        synchronized (this) {
        	if(state != R.layout.flying && state != R.layout.main)
        		return;
            ((PowerManager)getSystemService(Context.POWER_SERVICE)).userActivity(SystemClock.uptimeMillis(), false);
            if(sensors.containsKey(event.sensor)){
            	double a = d(values);
	            double ad = Math.abs(a - SensorManager.GRAVITY_EARTH);
	            if(ad > SensorManager.GRAVITY_EARTH/2) {
	            	vibrator.cancel();
	            	
	            	
	            	if(state != R.layout.flying)
	            		H.post(new Runnable() {

							public void run() {
								enterState(R.layout.flying);								
							}
	            			
	            	});	
	            	H.removeCallbacks(runFlightOver);
	            	H.postDelayed(runFlightOver, 300);
	            	
	            }
	            
            	SensorEvent previous = sensors.get(event.sensor);
            	if(null != previous){
            		if(event.timestamp < sensors.get(event.sensor).timestamp) {
            			Log.d("BOMBER","In the future "+(event.timestamp - previous.timestamp));
            		}
            		else {
            			double dur = (event.timestamp - previous.timestamp)/1000000000.0;
            			score += Math.abs(SensorManager.GRAVITY_EARTH - a) * dur;
            		}
            	}
	            sensors.put(event.sensor, event);
            }

        }
    }
    

    int sfx(){
    	switch(state){
    	//case R.layout.main: return R.raw.main;
    	case R.layout.fail: return R.raw.fail;
    	case R.layout.flying: return R.raw.flying;
    	case R.layout.congrats: return R.raw.congrats;
    	}
    	assert(false);
    	return R.raw.flying;
    }
    static boolean isPlaying(MediaPlayer mp){
    	try {
    		return mp.isPlaying();
    	} catch (IllegalStateException e) {
    		return false;
    	}
    }
    void enterState(int s){
    	if(state == s)return;
    	//Log.d("BomberAgent","Entering state " + s);
    	state = s;
    	if(videoHolder != null){
    		try {
    			videoHolder.stopPlayback();
    		}catch(Exception e) {
    			Log.d("BomberAgent","video stopping failed",e);
    		}
    		videoHolder = null;
    	}
    	for(MediaPlayer mp : MPS.values())
    		if(isPlaying(mp)) {
    			//Log.d("BomberAgent","Stopping sound");
    			mp.setOnCompletionListener(null);
    			
    			try {
    				mp.stop();
					mp.prepare();
				} catch (IllegalStateException e) {
				} catch (IOException e) {
				}
    		}
    	
    	setContentView(s);
    	
    	switch(state){
    	case R.layout.congrats: 
    		showScore();
    		break;
    	case R.layout.main:
    		score = 0;
    		break;
    	}
    	
    	startClip();
    }
    
    void showScore(){
    	TextView sv = (TextView)findViewById(R.id.score);
		 	
    	try {
    		Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/scorefont.ttf");
        	Log.d("BomberAgent","typeface " + typeface);
        	sv.setTypeface(typeface);
    	}catch(Exception e){
    		Log.d("BomberAgent","failed to set typeface",e);
    	}
        Log.d("BomberAgent","score " + score);
        sv.setText(""+(long)Math.ceil(100*score));
    }
    
    VideoView videoHolder = null;
    
    void doneClip(){
    	//Log.d("BomberAgent","Finished clip in " + state);
    	switch(state){
    	case R.layout.intro:
    		enterState(R.layout.main);
    		return;
    	case R.layout.main: 
    		enterState(R.layout.fail);
    		return;
    	case R.layout.flying:
    		return;
    	case R.layout.fail:
    		enterState(R.layout.intro);
    		return;
    	case R.layout.congrats:
    		enterState(R.layout.main);
    		return;
    	}
    }
    
    void startClip(){
    	
    	OnCompletionListener done = new OnCompletionListener(){
    		public void onCompletion(MediaPlayer mp) {
				doneClip();
    		}
    	};
    
    	int clipno = R.raw.main;
    	switch(state) {
    	case R.layout.intro:
    		videoHolder = (VideoView)findViewById(R.id.videointro);
    		clipno = R.raw.intro;
    		break;
    	case R.layout.main:
    		videoHolder = (VideoView)findViewById(R.id.videomain);
    	}
    	
    	if(videoHolder != null){
		    
			videoHolder.setVideoURI(Uri.parse("android.resource://com.socialrank.BomberAgent/" + clipno));
		    
			videoHolder.setOnCompletionListener(done);
			videoHolder.start();
    		return;
    	}
    	
    	MediaPlayer mp = MPS.get(sfx());
    	if(mp == null) {
    		Log.d("BomberAgent","no clip for state " + state);
    		doneClip();
    		return;
    	}
    	
    	mp.setOnCompletionListener(done);
    	
    	mp.start();
    
    }   
    
    public static double d(float[]values){
    	double ret = 0;
    	for(int i=0;values.length>i;++i)
    		ret += values[i]*values[i];
    	return Math.sqrt(ret);
    }
}