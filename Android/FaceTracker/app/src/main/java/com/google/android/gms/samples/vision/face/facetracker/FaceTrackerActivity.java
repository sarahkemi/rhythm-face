/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.samples.vision.face.facetracker;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.multidex.MultiDex;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.samples.vision.face.facetracker.ui.camera.CameraSourcePreview;
import com.google.android.gms.samples.vision.face.facetracker.ui.camera.GraphicOverlay;
import com.google.android.gms.samples.vision.face.facetracker.utility.MathUtility;
import com.google.android.gms.samples.vision.face.facetracker.utility.SoundMeter;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */
public final class FaceTrackerActivity extends AppCompatActivity {
    public static final String TAG = "FaceTracker";

    // Master switch for all debugging
    public static final boolean DEBUG = false;
    // Minor switches for debugging
    public static final boolean DEBUG_MEASURE_AUDIO = false;
    public static final boolean DEBUG_AUDIO_CHART = false;
    public static final boolean DEBUG_VIDEO_FACE_INFO = false;
    public static final boolean DEBUG_VIDEO_SECTIONS = true;

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private static LineChart chart;
    private static LineDataSet dataSet;
    private static LineData data;
    private static ArrayList<Entry> yVal;

    private static final int audioSamplerInterval = 30;
    private static final int audioDisplayTime = 10;
    private Handler audioSamplerHandler;
    private Runnable audioSamplerRunnable;
    private SoundMeter soundMeter;
    private boolean isRecording;
    private boolean movingToCenter = false;
    private int index = 1;
    private int btConnectRetryCount = 0;
    private int maxBtConnectRetry = 5;

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    public static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 1;

    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice = null;
    BluetoothAdapter mBluetoothAdapter;

    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    private static class AudioSamplerHandler extends Handler {
        @Override
        public void handleMessage(Message msg){
            if(msg.what == 0){
                if (msg.arg1 != 0) {
                    // We want to change the data in the chart, but keep it the same size
                    // So we just shift all the data down
                    for(int i = 0; i < yVal.size() - 1; i++) {
                        yVal.set(i, new Entry(yVal.get(i + 1).getVal(), i));
                    }
                    yVal.set(yVal.size() - 1, new Entry(msg.arg1, yVal.size()));
                    dataSet = new LineDataSet(yVal, "Amplitude");
                    data.notifyDataChanged();
                    chart.notifyDataSetChanged();
                    chart.invalidate();
                }
            }else{
                Log.e(TAG, "Handler received msg with what of " + msg.what);
            }
        }
    }

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main);

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);

        if (DEBUG && DEBUG_MEASURE_AUDIO) {
            isRecording = true;
            soundMeter = new SoundMeter();

            // Receives data from the audio sampler
            // We use a handler for better performance when rapidly polling
            audioSamplerHandler = new AudioSamplerHandler();

            // Runnable that samples the audio amplitude and sends the data back
            audioSamplerRunnable = new Runnable() {

                Message msg;

                public void run() {
                    msg = Message.obtain();
                    msg.arg1 = (int) soundMeter.getAmplitude();
                    msg.what = 0;
                    audioSamplerHandler.sendMessage(msg);
                    // Repeats the sampling infinitely until recording is stopped
                    if (isRecording) {
                        audioSamplerHandler.postDelayed(this, audioSamplerInterval);
                    }
                }
            };
        }
        // Checks for permissions and requests them if they are not given
        if (checkAndRequestPermissions()) {
            if (DEBUG && DEBUG_AUDIO_CHART) {
                createChart();
            } else {
                chart = (LineChart) findViewById(R.id.chart);
                chart.setVisibility(View.GONE);
            }
            createCameraSource();
        }

    }

    /**
     * Sets up the audio chart
     */
    private void createChart() {
        chart = (LineChart) findViewById(R.id.chart);

        yVal = new ArrayList<>();
        ArrayList<String> xVal = new ArrayList<>();
        // Create a chart of the correct size and fill it with empty data
        for (int i = 1; i <= (audioDisplayTime * (1000 / audioSamplerInterval)); i++) {
            xVal.add("" + i);
            yVal.add(new Entry(0, index));
            index++;
        }

        dataSet = new LineDataSet(yVal, "Amplitude");
        dataSet.setDrawCircles(false);

        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(dataSet);
        data = new LineData(xVal, dataSets);

        data.setDrawValues(false);

        chart.setData(data);
        // Basically hides everything but the actual data
        chart.setDescription("");
        chart.setGridBackgroundColor(Color.argb(100, 255, 255, 255));
        YAxis left = chart.getAxisLeft();
        YAxis right = chart.getAxisRight();
        XAxis bottom = chart.getXAxis();
        Legend legend = chart.getLegend();
        left.setEnabled(false);
        right.setEnabled(false);
        bottom.setEnabled(false);
        legend.setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);

        chart.invalidate();
    }

    /**
     * Creates and starts the camera.
     */
    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setMode(FaceDetector.ACCURATE_MODE)
                .build();

        detector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());

        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        mCameraSource = new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(640, 480)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    public void setupDevice() {

        if (mmSocket != null && mmSocket.isConnected()) {
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        mmSocket = null;
        mmDevice = null;
        mBluetoothAdapter = null;

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Make sure bluetooth is enabled
        if (!mBluetoothAdapter.isEnabled()) {
            Log.i(TAG, "Bluetooth is not enabled, asking to enable it");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }

        mmDevice = mBluetoothAdapter.getRemoteDevice("00:1A:7D:DA:71:13");
        Log.i(TAG, "Setup device!");

    }

    public void sendBtMsg(final String msg2send){
        //UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
        UUID uuid = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee"); //Standard SerialPortService ID
        try {

            if (mmSocket == null) {
                mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            }

            if (!mmSocket.isConnected()){
                mmSocket.connect();
            }

            OutputStream mmOutputStream = mmSocket.getOutputStream();
            mmOutputStream.write(msg2send.getBytes());

            Log.i(TAG, "send bt msg "+ msg2send);

        } catch (IOException e) {
            if (btConnectRetryCount < maxBtConnectRetry) {
                btConnectRetryCount++;
                Log.e(TAG, "There was an IOException when trying to send a message to the device");
                Log.e(TAG, "This was likely caused by the device being disconnected at the time");
                Log.e(TAG, "The app will now try to connect to the device again");
                setupDevice();
                sendBtMsg(msg2send);
            }
            else {
                // Offer to retry or quit app
//                new AlertDialog.Builder(this)
//                        .setTitle("Bluetooth Error")
//                        .setMessage("Cannot reach the phone stand! Are you sure the stand is nearby and powered on?")
//                        .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialogInterface, int i) {
//                                btConnectRetryCount = 0;
//                                sendBtMsg(msg2send);
//                            }
//                        })
//                        .setNegativeButton("Exit app", new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialogInterface, int i) {
//                                FaceTrackerActivity.this.finishAffinity();
//                            }
//                        })
//                        .setIcon(android.R.drawable.ic_dialog_alert)
//                        .create();
                this.finishAffinity();
            }
        }

    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();

        setupDevice();
        isRecording = true;
        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();

        if (DEBUG && DEBUG_MEASURE_AUDIO) {
            isRecording = false;
            soundMeter.stop();
        }
        mPreview.stop();
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (DEBUG && DEBUG_MEASURE_AUDIO) {
            soundMeter.stop();
        }
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        MultiDex.install(this);
    }

    /**
     * Makes sure all the permssions are granted
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        Log.d(TAG, "Permission callback called-------");
        switch (requestCode) {
            case REQUEST_ID_MULTIPLE_PERMISSIONS: {

                Map<String, Integer> perms = new HashMap<>();
                // Initialize the map with both permissions
                perms.put(Manifest.permission.CAMERA, PackageManager.PERMISSION_GRANTED);
                perms.put(Manifest.permission.RECORD_AUDIO, PackageManager.PERMISSION_GRANTED);
                perms.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                // Fill with actual results from user
                if (grantResults.length > 0) {
                    for (int i = 0; i < permissions.length; i++)
                        perms.put(permissions[i], grantResults[i]);
                    // Check for both permissions
                    if (perms.get(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            && perms.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                            && perms.get(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "all permissions granted");
                        // process the normal flow
                        //else any one or both the permissions are not granted
                        createCameraSource();
                    } else {
                        Log.d(TAG, "Some permissions are not granted, ask again ");
                        //permission is denied (this is the first time, when "never ask again" is not checked) so ask again explaining the usage of permission
//                        // shouldShowRequestPermissionRationale will return true
                        //show the dialog or snackbar saying its necessary and try again otherwise proceed with setup.
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                            showDialogOK("Camera, audio, and external storage permissions are required for this app",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            switch (which) {
                                                case DialogInterface.BUTTON_POSITIVE:
                                                    checkAndRequestPermissions();
                                                    break;
                                                case DialogInterface.BUTTON_NEGATIVE:
                                                    // proceed with logic by disabling the related features or quit the app.
                                                    break;
                                            }
                                        }
                                    });
                        }
                        //permission is denied (and never ask again is  checked)
                        //shouldShowRequestPermissionRationale will return false
                        else {
                            Toast.makeText(this, "Go to settings and enable permissions", Toast.LENGTH_LONG)
                                    .show();
                            //                            //proceed with logic by disabling the related features or quit the app.
                            this.finishAffinity();
                        }
                    }
                }
            }
        }

    }

    /**
     * Shows a basic OK/Cancel dialog
     * @param message Message to show
     * @param okListener Listener called when "OK" is pressed
     */
    private void showDialogOK(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", okListener)
                .create()
                .show();
    }

    /**
     * CHecks if permissions are granted; if not, request them
     * @return True if all permissions are granted; false otherwise
     */
    private  boolean checkAndRequestPermissions() {
        int permissionCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int permissionStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permissionAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        List<String> listPermissionsNeeded = new ArrayList<>();
        if (permissionCamera != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.CAMERA);
        }
        if (permissionStorage != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (permissionAudio != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]),REQUEST_ID_MULTIPLE_PERMISSIONS);
            return false;
        }
        return true;
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        if (DEBUG && DEBUG_MEASURE_AUDIO) {
            soundMeter.start();
            audioSamplerHandler.postDelayed(audioSamplerRunnable, audioSamplerInterval);
        }
        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private final GraphicOverlay mOverlay;
        private final FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            //mFaceGraphic.updateFace(face);
            int sizeX = mOverlay.getWidth();
            int sizeY = mOverlay.getHeight();
            float scaleX = (float) sizeX / (float) mCameraSource.getPreviewSize().getHeight();
            float scaleY = (float) sizeY / (float) mCameraSource.getPreviewSize().getWidth();
            int faceX = (int) MathUtility.getFaceX(face, sizeX, scaleX);
            int faceY = (int) MathUtility.getFaceY(face, sizeY, scaleY);

            String loc = MathUtility.getFaceLocation(sizeX, sizeY, faceX, faceY);
            if (movingToCenter) {
                if (loc.equals("Center")) {
                    movingToCenter = false;
                    sendBtMsg("stop");
                }
            } else if (loc.charAt(2) == '2') {
                movingToCenter = true;
                switch(loc.charAt(0)) {
                    case 'U':
                        sendBtMsg("up");
                        break;
                    case 'D':
                        sendBtMsg("down");
                        break;
                    case 'L':
                        sendBtMsg("right");
                        break;
                    case 'R':
                        sendBtMsg("left");
                        break;
                    default:
                        Log.e(TAG, "weird thing: " + loc);
                        break;
                }
            }

            mFaceGraphic.updateFace(face, loc);
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }
}
