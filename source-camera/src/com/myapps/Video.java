package com.myapps;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import com.myapps.utils.CouldNotCreateGroupException;
import com.myapps.utils.drawRectOnTouchView;
import com.myapps.utils.notificationLauncher;
import com.myapps.utils.snapShotManager;

import de.mjpegsample.MjpegView.MjpegInputStream;
import de.mjpegsample.MjpegView.MjpegView;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 
 * Implements the main video viewer interface
 * 
 */
public class Video extends Activity {
    private String url;
    private Camera cam;
    private CameraControl camC;
    private Activity activity;
    private MjpegView mv;
    private boolean pause;
    private boolean advanceCtrl = false;
    private boolean MDWindowSelector = false;
    private Thread t;
    private int id;
    HttpURLConnection videoCon;
    InputStream stream;
    private String fileNameURL = "/sdcard/com.myapps.camera/";
    private PowerManager.WakeLock wl;
    private TouchListener customTouchListener;

    /**
     * Called when activity starts or resumes
     */
    public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.video);

	setRequestedOrientation(0);
	PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
	wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tags");
	activity = this;
	id = 0;

	/* Recover arguments */

	Bundle extras = getIntent().getExtras();
	cam = (Camera) extras.getSerializable(getString(R.string.camTag));

	TextView tv = (TextView) findViewById(R.id.idTV);
	tv.setText("ID : " + cam.uniqueID + "-" + cam.id);
	/* Check network info */
	ConnectivityManager mConnectivity = (ConnectivityManager) activity
		.getApplicationContext().getSystemService(
			Context.CONNECTIVITY_SERVICE);
	NetworkInfo info = mConnectivity.getActiveNetworkInfo();
	if (info != null && info.isConnected()) {
	    int netType = info.getType();
	    if (netType == ConnectivityManager.TYPE_WIFI) {
		Log.i(getString(R.string.logTag), "Wifi detecte");
		url = "axis-cgi/mjpg/video.cgi?resolution=320x240&camera="
			+ String.valueOf(cam.channel);
	    } else {
		Log.i(getString(R.string.logTag), "Reseau detecte");
		url = "axis-cgi/mjpg/video.cgi?resolution=160x120&camera="
			+ String.valueOf(cam.channel);
	    }

	    camC = new CameraControl(cam, this);

	    mv = (MjpegView) findViewById(R.id.surfaceView1);
	    start_connection(mv, url);
	    Log.i("AppLog", "new TouchListener");
	    customTouchListener = new TouchListener(camC);
	    mv.setOnTouchListener(customTouchListener);
	} else {
	    Log.i(getString(R.string.logTag), "Aucun réseau");
	    Toast.makeText(activity.getApplicationContext(),
		    getString(R.string.messageConnexion), Toast.LENGTH_LONG)
		    .show();
	    finish();
	}
    }

    private class MyOnClickListenerControl implements OnClickListener {
	float value0, value1;
	int function;

	public MyOnClickListenerControl(int function, float value0, float value1) {
	    this.function = function;
	    this.value0 = value0;
	    this.value1 = value1;
	}

	@Override
	public void onClick(View v) {
	    camC.changeValFunc(function, value0, value1);
	}

    }

    /**
     * Assign custom menu to activity
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
	MenuInflater inflater = getMenuInflater();
	inflater.inflate(R.menu.menu_video, menu);
	return true;
    }

    /**
     * Implements Menu Items Listener
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
	RelativeLayout screen = (RelativeLayout) findViewById(R.id.RelativeLayout01);
	LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	switch (item.getItemId()) {
	case R.id.menu_control:
	    if (!advanceCtrl) {
		if (MDWindowSelector) {
		    MDWindowSelector = false;
		    screen.removeView(findViewById(R.id.mds_video));
		}
		advanceCtrl = true;
		inflater.inflate(R.layout.adv_video, screen, true);

		/* Buttons Listener */
		Button buttonSnap = (Button) findViewById(R.id.Snap);
		buttonSnap.setOnClickListener(new OnClickListener() {
		    @Override
		    /**
		     * Show resolution dialog, get Snapshot and record it
		     */
		    public void onClick(View v) {
			AlertDialog.Builder builder = new AlertDialog.Builder(
				activity);
			builder.setTitle(getString(R.string.snapshotFormat));
			final String[] resolutions = camC.getResolutions();
			builder.setSingleChoiceItems(resolutions, -1,
				new DialogInterface.OnClickListener() {
				    public void onClick(DialogInterface dialog,
					    int item) {
					try {
					    String fileName = "Snap-"
						    + System.currentTimeMillis()
						    + ".jpeg";
					    Bitmap bmp = camC
						    .takeSnapshot(resolutions[item]);
					    snapShotManager.saveSnap(bmp,
						    fileNameURL, fileName);
					    notificationLauncher
						    .statusBarNotificationImage(
							    activity,
							    bmp,
							    (getString(R.string.snapshotSaved) + fileName),
							    fileNameURL
								    + fileName,
							    id,
							    "" + cam.uniqueID);
					    id++;
					} catch (IOException e) {
					    Log.i(getString(R.string.logTag),
						    "Snap I/O exception !!");
					    e.printStackTrace();
					}
					dialog.dismiss();
				    }
				});
			AlertDialog alert = builder.create();
			alert.show();
		    }
		});
		buttonSnap.setEnabled(camC.getResolutions() != null);

		Button buttonIrisP = (Button) findViewById(R.id.IrisP);
		buttonIrisP.setOnClickListener(new MyOnClickListenerControl(
			CameraControl.IRIS, 250, 0));
		buttonIrisP.setEnabled(camC.isSupported(CameraControl.IRIS));

		Button buttonIrisM = (Button) findViewById(R.id.IrisM);
		buttonIrisM.setOnClickListener(new MyOnClickListenerControl(
			CameraControl.IRIS, -250, 0));
		buttonIrisM.setEnabled(camC.isSupported(CameraControl.IRIS));

		Button buttonFocusP = (Button) findViewById(R.id.FocusP);
		buttonFocusP.setOnClickListener(new MyOnClickListenerControl(
			CameraControl.FOCUS, 2500, 0));
		buttonFocusP.setEnabled(camC.isSupported(CameraControl.FOCUS));

		Button buttonFocusM = (Button) findViewById(R.id.FocusM);
		buttonFocusM.setOnClickListener(new MyOnClickListenerControl(
			CameraControl.FOCUS, -2500, 0));
		buttonFocusM.setEnabled(camC.isSupported(CameraControl.FOCUS));

		Button buttonBrightnessP = (Button) findViewById(R.id.BrightnessP);
		buttonBrightnessP
			.setOnClickListener(new MyOnClickListenerControl(
				CameraControl.BRIGHTNESS, 2500, 0));
		buttonBrightnessP.setEnabled(camC
			.isSupported(CameraControl.BRIGHTNESS));

		Button buttonBrightnessM = (Button) findViewById(R.id.BrightnessM);
		buttonBrightnessM
			.setOnClickListener(new MyOnClickListenerControl(
				CameraControl.BRIGHTNESS, -2500, 0));
		buttonBrightnessM.setEnabled(camC
			.isSupported(CameraControl.BRIGHTNESS));

		Button buttonIROn = (Button) findViewById(R.id.IROn);
		buttonIROn.setOnClickListener(new OnClickListener() {
		    @Override
		    public void onClick(View v) {
			camC.switchAutoFunc(CameraControl.AUTO_IR, "on");
		    }
		});
		buttonIROn
			.setEnabled(camC.isSupported(CameraControl.IR_FILTER));

		Button buttonIROff = (Button) findViewById(R.id.IROff);
		buttonIROff.setOnClickListener(new OnClickListener() {
		    @Override
		    public void onClick(View v) {
			camC.switchAutoFunc(CameraControl.AUTO_IR, "off");
		    }
		});
		buttonIROff.setEnabled(camC
			.isSupported(CameraControl.IR_FILTER));

		Button backlightOn = (Button) findViewById(R.id.BacklightOn);
		backlightOn.setOnClickListener(new OnClickListener() {
		    @Override
		    public void onClick(View v) {
			camC.switchAutoFunc(CameraControl.BACKLIGHT, "on");
		    }
		});
		backlightOn.setEnabled(camC
			.isSupported(CameraControl.BACKLIGHT));

		Button backlightOff = (Button) findViewById(R.id.BacklightOff);
		backlightOff.setOnClickListener(new OnClickListener() {
		    @Override
		    public void onClick(View v) {
			camC.switchAutoFunc(CameraControl.BACKLIGHT, "off");
		    }
		});
		backlightOff.setEnabled(camC
			.isSupported(CameraControl.BACKLIGHT));
	    } else {
		advanceCtrl = false;
		screen.removeView(findViewById(R.id.englobe));
	    }
	    screen.invalidate();
	    return true;
	case R.id.menu_auto_focus:
	    camC.switchAutoFunc(CameraControl.AUTOFOCUS, "on");
	    return true;
	case R.id.menu_auto_ir:
	    camC.switchAutoFunc(CameraControl.AUTO_IR, "auto");
	    return true;
	case R.id.menu_auto_iris:
	    camC.switchAutoFunc(CameraControl.AUTOIRIS, "on");
	    return true;
	case R.id.menu_active_md:
	    if (camC.isEnabled(CameraControl.MOTION_D)) {
		if (!MDWindowSelector) {
		    if (advanceCtrl) {
			advanceCtrl = false;
			screen.removeView(findViewById(R.id.englobe));
		    }
		    inflater.inflate(R.layout.mds_video, screen, true);
		    Button ok = (Button) findViewById(R.id.okRectView);
		    if (MotionDetectionService.isAlreadyRunning(camC.cam) != -1) {
			ok.setText(R.string.boutonArreter);
			ok.invalidate();
		    }
		    ok.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
			    int indice;
			    Button ok = (Button) findViewById(R.id.okRectView);
			    Log.i(getString(R.string.logTag), "Camera : "
				    + camC.cam.id + camC.cam.groupID);
			    if ((indice = MotionDetectionService
				    .isAlreadyRunning(camC.cam)) != -1) {
				Log.i(getString(R.string.logTag), "Remove cam "
					+ indice);
				MotionDetectionService.stopRunningDetection(
					cam, activity.getApplication(), indice);
				try {
				    camC.removeMotionD();
				} catch (IOException e) {
				    e.printStackTrace();
				}
				ok.setText(R.string.run);
				ok.invalidate();
			    } else {
				try {
				    drawRectOnTouchView drawRect = (drawRectOnTouchView) findViewById(R.id.drawRect);
				    // A REMPLACER PAR LES PRIMITIVES AJOUTER UN
				    // DIALOG AVEC UNE
				    // BARRE POUR LA SENSIBILITE
				    int group = camC.addMotionD();
				    if (drawRect.isDraw()) {
					Log.i(getString(R.string.logTag),
						"Point : "
							+ drawRect.toString());
					PointF start = drawRect.getStart();
					PointF end = drawRect.getEnd();
					int absoluteTop = (int) (start.y * 10000 / drawRect
						.getBottom());
					int absoluteBottom = (int) (end.y * 10000 / drawRect
						.getBottom());
					int absoluteRight = (int) (end.x * 10000 / drawRect
						.getRight());
					int absoluteLeft = (int) (start.x * 10000 / drawRect
						.getRight());
					Log.i(getString(R.string.logTag),
						"top : " + absoluteTop
							+ " bottom : "
							+ absoluteBottom
							+ " right : "
							+ absoluteRight
							+ " left : "
							+ absoluteLeft);

					camC.updateMotionDParam("Top", ""
						+ absoluteTop);
					camC.updateMotionDParam("Bottom", ""
						+ absoluteBottom);
					camC.updateMotionDParam("Right", ""
						+ absoluteRight);
					camC.updateMotionDParam("Left", ""
						+ absoluteLeft);

				    }
				    camC.cam.setGroup(group);
				    Log.i(getString(R.string.logTag),
					    "Camera : " + camC.cam.id
						    + camC.cam.groupID);
				    Intent intent = new Intent(v.getContext(),
					    MotionDetectionService.class);
				    Bundle objetbunble = new Bundle();
				    objetbunble.putSerializable(
					    getString(R.string.camTag),
					    camC.cam);
				    intent.putExtras(objetbunble);
				    int lim = Integer.parseInt(Home.preferences
					    .getString(
						    getString(R.string.SeuilDM),
						    getString(R.string.defaultSeuilDM)));
				    long delay = Long.parseLong(Home.preferences
					    .getString(
						    getString(R.string.NotifTO),
						    getString(R.string.defaultNotifTO)));
				    intent.putExtra("limit", lim);
				    intent.putExtra("delay", delay);
				    Log.i(getString(R.string.logTag),
					    "Start service");
				    startService(intent);
				    ok.setText(R.string.boutonArreter);
				    ok.invalidate();
				} catch (IOException e) {
				    e.printStackTrace();
				} catch (CouldNotCreateGroupException e) {
				    Log.i(getString(R.string.logTag),
					    "CouldNotCreateGroupException");
				    e.printStackTrace();
				}
			    }
			}
		    });
		    MDWindowSelector = true;
		} else {
		    MDWindowSelector = false;
		    screen.removeView(findViewById(R.id.mds_video));
		}
		screen.invalidate();
	    } else {
		Toast.makeText(activity.getApplicationContext(),
			getString(R.string.messageMDError), Toast.LENGTH_LONG)
			.show();

	    }
	    return true;
	}

	return false;
    }

    /**
     * Create and start the Mjpeg video
     * 
     * @param mv
     * @param url
     * @param cam
     */
    private void start_connection(MjpegView mv, String url) {
	try {
	    videoCon = camC.sendCommand(url);
	    stream = videoCon.getInputStream();
	    mv.setSource(new MjpegInputStream(stream));
	    mv.setDisplayMode(MjpegView.SIZE_FULLSCREEN);
	    mv.showFps(true);
	    pause = false;

	} catch (IOException e) {
	    Log.i(getString(R.string.logTag), "StartConnect IOException");
	    Toast.makeText(activity.getApplicationContext(),
		    getString(R.string.messageCamError), Toast.LENGTH_LONG)
		    .show();
	    e.printStackTrace();
	    finish();
	}
    }

    /**
     * Resume video and acquire wakelock when activity resumes
     */
    public void onResume() {
	super.onResume();
	wl.acquire();
	if (pause) {
	    mv.resumePlayback();
	    pause = false;
	}

    }

    /**
     * Stop video and release wakelock when activity sleeps
     */
    public void onPause() {
	pause = true;
	wl.release();
	if (mv != null)
	    mv.stopPlayback();
	super.onPause();
    }

    /**
     * Stop Video before destroy
     */
    public void onDestroy() {
	if (mv != null)
	    mv.stopPlayback();
	if (videoCon != null) {
	    try {
		stream.close();
		videoCon.disconnect();
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}
	super.onDestroy();
    }
}
