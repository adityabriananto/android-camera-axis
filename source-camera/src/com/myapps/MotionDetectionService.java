package com.myapps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import android.app.Application;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;

import com.myapps.utils.base64Encoder;
import com.myapps.utils.notificationLauncher;
import com.myapps.utils.snapShotManager;

/**
 * 
 * Service used to leave Motion Detection run in the background
 * and alert the user by notification
 *
 */
public class MotionDetectionService extends Service {
    private static final String TAG = "AppLog";
    private int limit;
    private long delay;
    private Thread t;
    static int START_ID = 10;
    static String fileNameURL = "/sdcard/com.myapps.camera/";
    public static boolean detected = false;

    public final static int MVTMSG = 5;
    private static Vibrator vibreur;

    /**
     * Handler to process detected movements
     */
    public static Handler myViewUpdateHandler = new Handler() {
	public void handleMessage(Message msg) {
	    if (msg.what == MVTMSG) {
		Log.i(TAG, "Mouvement !");
		/* detected required for Junit test */
		detected = true;
		HttpURLConnection con;
		try {
		    con = sendCommand(currentMDCam.get(msg.arg1),
			    "axis-cgi/jpg/image.cgi");
		    InputStream stream = con.getInputStream();
		    Bitmap bmp = BitmapFactory.decodeStream(stream);
		    snapShotManager.saveSnap(bmp, fileNameURL, ("MD-time-"
			    + System.currentTimeMillis() + ".jpeg"));
		    stream.close();
		    con.disconnect();
		} catch (IOException e) {
		    e.printStackTrace();
		}
		vibreur.vibrate(1000);
	    }
	    super.handleMessage(msg);
	}
    };

    public static ArrayList<Thread> currentMD;
    public static ArrayList<Camera> currentMDCam;

    public void onCreate() {
	super.onCreate();
	Log.i(TAG, "onCreate");
	currentMD = new ArrayList<Thread>();
	currentMDCam = new ArrayList<Camera>();

	vibreur = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    /**
     * Open a HttpURLConnection to (camera.getUri+command) with authorization
     * @param cam The camera to which to connect
     * @param command The URL part used for the connection
     * @return The HttpURLConnection object
     * @throws IOException
     */
    public static HttpURLConnection sendCommand(Camera cam, String command)
	    throws IOException {
	URL url = null;
	HttpURLConnection con = null;
	url = new URL(cam.getURI() + command);
	con = (HttpURLConnection) url.openConnection();
	con.setDoOutput(true);
	con.setRequestProperty("Authorization",
		base64Encoder.userNamePasswordBase64(cam.login, cam.pass));
	con.connect();
	return con;
    }

    /**
     * Check whether a instance of MotionDetectionService is running
     * @param c The Camera object used for Motion Detection
     * @return -1 if the Motion Detection is not running, the position in
     * 		the list of Motion Detection instances otherwise
     */
    public static int isAlreadyRunning(Camera c) {
	for (int i = 0; i < currentMDCam.size(); i++) {
	    if (currentMDCam.get(i).uniqueID == c.uniqueID) {
		if (currentMDCam.get(i).groupID == c.groupID) {
		    return i;
		}
	    }
	}
	return -1;
    }

    /**
     * Stop a running instance of MotionDetectionService
     * @param c The Camera object used for Motion Detection window
     * @param app The application for which the notification is running
     * @param indice The position in the list of running Motion Detection units
     * @return true if the Motion Detection has successful been removed,
     * 		false otherwise
     */
    public static boolean stopRunningDetection(Camera c, Application app,
	    int indice) {
	if (indice != -1) {
	    currentMDCam.remove(indice);
	    currentMD.get(indice).interrupt();
	    currentMD.remove(indice);
	    notificationLauncher.removeStatusBarNotificationRunning(app,
		    c.getMotionDetectionID(START_ID));
	    Log.i(TAG,
		    "Motion Detection remove notif"
			    + c.getMotionDetectionID(START_ID));
	    return true;
	}
	return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
	Camera cam = null;
	/* Recover arguments */
	Bundle extras = intent.getExtras();
	if (extras == null) {
	    /* Any argument = start service for initialization */
	    return START_NOT_STICKY;
	}

	cam = (Camera) extras.getSerializable(getString(R.string.camTag));
	limit = extras.getInt("limit");
	delay = extras.getLong("delay");
	Log.i(TAG, "onStart " + cam.uniqueID + "-" + cam.getId() + "-"
		+ cam.groupID);

	Intent notificationIntent = new Intent(getApplicationContext(),
		Video.class);

	Bundle objetbunble = new Bundle();
	objetbunble.putSerializable(getString(R.string.camTag), cam);
	notificationIntent.putExtras(objetbunble);
	notificationIntent.setAction(Intent.ACTION_MAIN);
	/*
	 * Set data because filterEquals() compares intents without extras
	 * !!!!!!!!!
	 */
	notificationIntent.setDataAndType(
		Uri.parse("" + cam.uniqueID + cam.groupID), "Camera");
	PendingIntent contentIntent = PendingIntent.getActivity(
		getApplicationContext(), 0, notificationIntent, 0);
	/*
	 * ID format : StartId + 10 * UniqueId + GroupId [0-9]
	 * It avoids conflicts 
	 */
	notificationLauncher.statusBarNotificationRunning(
		this.getApplication(), contentIntent, START_ID
			+ (cam.uniqueID * 10) + cam.groupID,
		"Motion Detection " + cam.uniqueID + "-" + cam.getId() + "-"
			+ cam.groupID);
	Log.i(TAG,
		"Motion Detection notif" + cam.getMotionDetectionID(START_ID));
	t = new Thread(new serviceWork(cam, currentMDCam.size()));
	currentMDCam.add(cam);
	currentMD.add(t);
	t.start();
	return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
	int size = currentMD.size();
	Camera c;
	for (int i = 0; i < size; i++) {
	    c = currentMDCam.get(i);
	    currentMDCam.remove(i);
	    currentMD.get(i).interrupt();
	    currentMD.remove(i);
	    notificationLauncher.removeStatusBarNotificationRunning(
		    this.getApplication(), c.getMotionDetectionID(START_ID));
	}
	super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
	Log.i(TAG, "onBind");
	return null;
    }

    /**
     * 
     * Runnable analyzing motion data and alerting if movements
	 * have been detected (level > threshold)
     *
     */
    private class serviceWork implements Runnable {
	Camera cam;
	int index;

	public serviceWork(Camera cam, int index) {
	    this.cam = cam;
	    this.index = index;
	}

	@Override
	public void run() {
	    HttpURLConnection con;
	    Log.i(TAG, "Thread run");
	    try {
		con = sendCommand(cam, "axis-cgi/motion/motiondata.cgi?group="
			+ cam.groupID);
		InputStreamReader isr = new InputStreamReader(
			con.getInputStream());
		BufferedReader br = new BufferedReader(isr);
		String s;
		int lvlc, lvlb, lvlf;
		long last = System.currentTimeMillis();
		while (!Thread.currentThread().isInterrupted()) {
		    while ((s = br.readLine()) == null) {
			Log.i(TAG, "No data => sleep");
			Thread.sleep(100);
		    }
		    Log.i(TAG, s);
		    if (s.contains("level=") == true) {
			Log.i(TAG, s);
			lvlc = s.indexOf("level=");
			s = s.substring(lvlc);
			lvlb = s.indexOf("=");
			lvlf = s.indexOf(";");
			s = s.substring(lvlb + 1, lvlf);
			if (Integer.parseInt(s) > limit) {
			    Message m = new Message();
			    m.what = MotionDetectionService.MVTMSG;
			    m.arg1 = index;
			    if (last + delay < System.currentTimeMillis()) {
				MotionDetectionService.myViewUpdateHandler
					.sendMessage(m);
				last = System.currentTimeMillis();
			    }
			}
		    }
		}
		con.disconnect();
	    } catch (IOException e) {
		Log.i(TAG, "MDService IOException");
		e.printStackTrace();
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }
	}
    }

}