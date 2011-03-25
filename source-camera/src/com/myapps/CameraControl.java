package com.myapps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import com.myapps.utils.CouldNotCreateGroupException;
import com.myapps.utils.base64Encoder;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

/**
 * 
 * CameraControl class implements remote camera control like PTZ
 * (Pan/Tilt/Zoom), Snapshot, iris, focus, etc...
 * 
 */
public class CameraControl {
    private static final long serialVersionUID = 1L;

    public static final int PAN = 0;
    public static final int TILT = 1;
    public static final int ZOOM = 2;
    public static final int FOCUS = 3;
    public static final int IRIS = 4;
    public static final int AUTOFOCUS = 5;
    public static final int AUTOIRIS = 6;
    public static final int IR_FILTER = 7;
    public static final int AUTO_IR = 8;
    public static final int BACKLIGHT = 9;
    public static final int MOTION_D = 10;
    public static final int AUDIO = 11;
    public static final int BRIGHTNESS = 12;

    private static final int NB_FUNC = 13;
    private static final int NB_BASIC_FUNC = 5;

    public static final int NOT_SUPPORTED = -1;
    public static final int DISABLED = 0;
    public static final int ENABLED = 1;

    public static final char ABSOLUTE = 1;
    public static final char RELATIVE = 2;
    public static final int DIGITAL = 4;
    public static final int AUTO = 8;
    public static final int CONTINUOUS = 16;

    public Camera cam;
    private int[] currentConfig = new int[NB_FUNC];
    private int[] functionProperties = new int[NB_BASIC_FUNC];
    private String[] resolutions, rotations, formats;
    private Activity activity;

    public CameraControl(Camera cam, Activity activity) {
		this.cam = cam;
		Application a;
		this.activity = activity;
		this.initConfig();
		this.loadConfig();
    }

    /** Initialize all the camera's functions to the NOT_SUPPORTED state */
    private void initConfig() {
	for (int i = 0; i < NB_FUNC; i++) {
	    this.currentConfig[i] = NOT_SUPPORTED;
	}
    }

	/** Request camera's possibilities from server and mark off them */
	private void loadConfig() {
		HttpURLConnection con;
		InputStream result;
		BufferedReader in;
		String line, property, value;

		try {
			con = sendCommand("axis-cgi/com/ptz.cgi?info=1&camera=" + String.valueOf(cam.channel));
			if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
				result = con.getInputStream();
				in = new BufferedReader(new InputStreamReader(
						result));
				while ((line = in.readLine()) != null) {
					if (line.indexOf("=") > -1) {
						property = line.substring(0, line.indexOf("=")).trim();
						value = line.substring(line.indexOf("=") + 1);
						if (property.contains("pan")) {
							if (property.contentEquals("pan"))
								functionProperties[PAN] += ABSOLUTE;
							else if (property.contentEquals("rpan"))
								functionProperties[PAN] += RELATIVE;
							else if (property
									.contentEquals("continuouspantiltmove")) {
								functionProperties[PAN] += CONTINUOUS;
								functionProperties[TILT] += CONTINUOUS;
							}
						} else if (property.contains("tilt")) {
							if (property.contentEquals("tilt"))
								functionProperties[TILT] += ABSOLUTE;
							else if (property.contentEquals("rtilt"))
								functionProperties[TILT] += RELATIVE;
						} else if (property.contains("zoom")) {
							if (property.contentEquals("zoom"))
								functionProperties[ZOOM] += ABSOLUTE;
							else if (property.contentEquals("rzoom"))
								functionProperties[ZOOM] += RELATIVE;
							else if (property
									.contentEquals("continuouszoommove"))
								functionProperties[ZOOM] += CONTINUOUS;
							else if (property.contentEquals("digitalzoom"))
								functionProperties[ZOOM] += DIGITAL;
						} else if (property.contains("focus")) {
							if (property.contentEquals("focus"))
								functionProperties[FOCUS] += ABSOLUTE;
							else if (property.contentEquals("rfocus"))
								functionProperties[FOCUS] += RELATIVE;
							else if (property
									.contentEquals("continuousfocusmove"))
								functionProperties[FOCUS] += CONTINUOUS;
							else if (property.contentEquals("autofocus")) {
								functionProperties[FOCUS] += AUTO;
								currentConfig[AUTOFOCUS] = DISABLED;
							}
						} else if (property.contains("iris")) {
							if (property.contentEquals("iris"))
								functionProperties[IRIS] += ABSOLUTE;
							else if (property.contentEquals("riris"))
								functionProperties[IRIS] += RELATIVE;
							else if (property
									.contentEquals("continuousirismove"))
								functionProperties[IRIS] += CONTINUOUS;
							else if (property.contentEquals("autoiris")) {
								functionProperties[IRIS] += AUTO;
								currentConfig[AUTOIRIS] = DISABLED;
							}
						} else if (property.contentEquals("ircutfilter")) {
							currentConfig[IR_FILTER] = DISABLED;
							if (value.contains("auto"))
								currentConfig[AUTO_IR] = DISABLED;
						} else if (property.contentEquals("backlight")) {
							currentConfig[BACKLIGHT] = DISABLED;
						}
					}
				}
				con.disconnect();
			}
			for (int i = 0; i < NB_BASIC_FUNC; i++)
				if (functionProperties[i] > 0)
					currentConfig[i] = ENABLED;
			
			con = sendCommand("axis-cgi/admin/param.cgi?action=list&group=" +
					"Properties.Motion.Motion,Properties.Audio.Audio," +
					"Properties.Image");
			if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
				result = con.getInputStream();
				in = new BufferedReader(new InputStreamReader(
						result));
				while ((line = in.readLine()) != null) {
					if (line.contains("Properties.Motion.Motion=yes"))
						currentConfig[MOTION_D] = ENABLED;
					else if (line.contains("Properties.Audio.Audio=yes"))
						currentConfig[AUDIO] = ENABLED;
					else if (line.contains("Properties.Image.Rotation")) {
						value = line.substring(line.indexOf("=") + 1);
						rotations = value.split(",");
					} else if (line.contains("Properties.Image.Resolution")) {
						value = line.substring(line.indexOf("=") + 1);
						resolutions = value.split(",");
						Log.i(activity.getString(R.string.logTag), value);
					} else if (line.contains("Properties.Image.Format")) {
						value = line.substring(line.indexOf("=") + 1);
						formats = value.split(",");
					}
				}
				for (int i = 0; i < NB_FUNC; i++)
					Log.i(activity.getString(R.string.logTag), "func" + i + ": "
							+ currentConfig[i]);
			}
			con.disconnect();
		} catch (IOException e) {
		    e.printStackTrace();
		}
    }

    /** Return the array containing the different resolutions used for snapshot */
    public String[] getResolutions() {
	return resolutions;
    }

    /** Return the array containing the different rotation angles of image */
    public String[] getRotations() {
	return rotations;
    }

    /** Return the array containing the different streaming file formats */
    public String[] getFormats() {
	return formats;
    }

    public boolean isEnabled(int function) {
	if (function < 0 || function >= NB_FUNC)
	    return false;
	return currentConfig[function] == ENABLED;
    }

    public boolean isSupported(int function) {
	if (function < 0 || function >= NB_FUNC)
	    return false;
	return currentConfig[function] != NOT_SUPPORTED;
    }

    public int enableFunction(int function) {
	if (function < 0 || function >= NB_BASIC_FUNC)
	    return 0;
	if (!isSupported(function))
	    return -1;
	this.currentConfig[function] = ENABLED;
	return 1;
    }

    public int disableFunction(int function) {
	if (function < 0 || function >= NB_BASIC_FUNC)
	    return 0;
	if (!isSupported(function))
	    return -1;
	this.currentConfig[function] = DISABLED;
	return 1;
    }

    /**
     * Get address's camera
     * 
     * @return address's camera
     */
    public String createURL() {
	return cam.getURI();
    }

    /**
     * Open a HttpURLConnection to (camera.getUri+command) with authorization
     * Exemple : camera.getUri = http://192.168.1.2/ command =
     * axis-cgi/com/ptz.cgi?info=1&camera=1
     * 
     * @param command
     * @return The HttpURLConnection
     */
    public HttpURLConnection sendCommand(String command) throws IOException {
	URL url = null;
	HttpURLConnection con = null;
	Log.i(activity.getString(R.string.logTag), command);
	url = new URL(createURL() + command);
	con = (HttpURLConnection) url.openConnection();
	int timeout = Integer.parseInt(Home.preferences.getString(
		activity.getString(R.string.TimeOut),
		activity.getString(R.string.defaultTimeOut)));
	con.setConnectTimeout(timeout);
	con.setDoOutput(true);
	con.setRequestProperty("Authorization",
		base64Encoder.userNamePasswordBase64(cam.login, cam.pass));
	con.connect();
	return con;
    }

    /**
     * Change the value of PTZ/Focus/Iris used by the camera (perform an action)
     */
    public boolean changeValFunc(int function, float value1, float value2) {
	// if (function < 0 || function >= NB_BASIC_FUNC)
	// return 0;
	// if (!isSupported(function))
	// return -1;

	String query = "";
	switch (function) {
	case PAN:
	case TILT:
	    query = "rpan=" + value1 + "&rtilt=" + value2;
	    break;
	case ZOOM:
	    query = "rzoom=" + value1;
	    break;
	case FOCUS:
	    query = "rfocus=" + value1;
	    break;
	case IRIS:
	    query = "riris=" + value1;
	    break;
	case BRIGHTNESS:
	    query = "rbrightness=" + value1;
	    break;
	}
	try {
	    HttpURLConnection con = sendCommand("axis-cgi/com/ptz.cgi?" + query
		    + "&camera=" + String.valueOf(cam.channel));
	    Log.i(activity.getString(R.string.logTag),
		    ("axis-cgi/com/ptz.cgi?" + query + "&camera=" + String
			    .valueOf(cam.channel)));
	    boolean res = con.getResponseCode() == HttpURLConnection.HTTP_NO_CONTENT;
	    con.disconnect();
	    return res;
	} catch (IOException e) {
	    e.printStackTrace();
	}
	return false;
    }

    /** Switch on/off the autofocus or the autoiris on the camera */
    public int switchAutoFunc(int function, String value) {
	if (function != AUTOFOCUS && function != AUTOIRIS
		&& function != AUTO_IR && function != BACKLIGHT)
	    return 0;
	/*
	 * if (!isSupported(function)) return -1;
	 */
	if (!value.equals("on") && !value.equals("off")
		&& !value.equals("auto"))
	    return 0;
	String param = "";
	switch (function) {
	case AUTOFOCUS:
	    param = "autofocus";
	    break;
	case AUTOIRIS:
	    param = "autoiris";
	    break;
	case AUTO_IR:
	    param = "ircutfilter";
	    break;
	case BACKLIGHT:
	    param = "backlight";
	    break;
	}
	try {
	    HttpURLConnection con = sendCommand("axis-cgi/com/ptz.cgi?" + param
		    + "=" + value + "&camera=" + String.valueOf(cam.channel));
	    if (con.getResponseCode() != HttpURLConnection.HTTP_NO_CONTENT) {
		if (value.equals("on"))
		    this.enableFunction(function);
		else
		    this.disableFunction(function);
		con.disconnect();
		return 1;
	    } else {
		con.disconnect();
		return 0;
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	}
	return 0;
    }

    /**
     * Get SnapShot from Axis Camera
     * 
     * @param resolution
     *            You can choose the snapshot resolution from : "1280x1024",
     *            "1280x960", "1280x720", "768x576", "4CIF", "704x576",
     *            "704x480", "VGA", "640x480", "640x360", "2CIFEXP", "2CIF",
     *            "704x288", "704x240", "480x360", "CIF", "384x288", "352x288",
     *            "352x240", "320x240", "240x180", "QCIF", "192x144", "176x144",
     *            "176x120", "160x120" (dependent on the camera)
     * @return The Bitmap created by the camera
     * @throws IOException
     *             If camera can't take snapshot or if the camera is unreachable
     */
    public Bitmap takeSnapshot(String resolution) throws IOException {
	HttpURLConnection con = sendCommand("axis-cgi/jpg/image.cgi"
		+ "?resolution=" + resolution + "&camera=" + String.valueOf(cam.channel));
	Log.i(activity.getString(R.string.logTag), "Snapshot");
	InputStream stream = con.getInputStream();
	Bitmap bmp = BitmapFactory.decodeStream(stream);
	stream.close();
	con.disconnect();
	return bmp;
    }

    public int addMotionD() throws IOException, CouldNotCreateGroupException {
	HttpURLConnection con = sendCommand("axis-cgi/operator/param.cgi?action=add&group=Motion&template=motion");
	if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
	    BufferedReader in = new BufferedReader(new InputStreamReader(
		    con.getInputStream()));
	    String line;
	    while ((line = in.readLine()) != null) {
		if (line.contains("HTTP/1.0 200 OK\r\n"))
		    continue;
		if (line.contains("# Request failed: Couldn't create group"))
		    throw new CouldNotCreateGroupException();
		if (line.contains("M")) {
		    cam.groupeID = Integer.parseInt(line.substring(1, 2));
		    Log.i(activity.getString(R.string.logTag), "MotionDetection groupe = " + cam.groupeID);
		    return cam.groupeID;
		}
	    }
	}
	throw new CouldNotCreateGroupException();
    }



    public int removeMotionD() throws IOException {
	HttpURLConnection con = sendCommand("axis-cgi/operator/param.cgi?action=remove&group=Motion.M"
		+ cam.groupeID);
	Log.i(activity.getString(R.string.logTag), con.getResponseCode()
		+ "MotionDetection free groupe = " + cam.groupeID);
	if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
	    cam.groupeID = -1;
	    return -1;
	}
	return cam.groupeID;
    }

    public boolean updateMotionDParam(String param, String value)
	    throws IOException {
	HttpURLConnection con = sendCommand("axis-cgi/operator/param.cgi?action=update&Motion.M"
		+ cam.groupeID + "." + param + "=" + value);
	if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
	    return true;
	}
	return false;
    }

    /* NEVER USE FOR THE MOMENT */
    /*public void getMotionDValues(int sensitivity, int limit, long delay)
	    throws IOException {
	if (!isEnabled(MOTION_D)) {
	    HttpURLConnection con = sendCommand("axis-cgi/"
		    + "operator/param.cgi?action=add&group=Motion&template="
		    + "motion&Motion.M.Sensitivity=" + sensitivity
		    + "Motion.M.ObjectSize=" + limit);
	    if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
		try {
		    InputStream result = con.getInputStream();
		    BufferedReader in = new BufferedReader(
			    new InputStreamReader(result));
		    String line = in.readLine();
		    int end = line.indexOf("OK") - 1;
		    String id = line.substring(1, end);
		} catch (IOException e) {
		    Log.i(activity.getString(R.string.logTag), "MotionDetection IOException");
		    e.printStackTrace();
		}
		enableFunction(MOTION_D);
		Log.i(activity.getString(R.string.logTag), "Detection activee");
	    }
	}
    }*/
}
