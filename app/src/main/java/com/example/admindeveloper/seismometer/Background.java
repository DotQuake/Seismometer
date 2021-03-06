package com.example.admindeveloper.seismometer;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.example.admindeveloper.seismometer.RealTimeServices.RealTimeController;
import com.example.admindeveloper.seismometer.UploadServices.ZipManager;

import net.gotev.uploadservice.MultipartUploadRequest;
import net.gotev.uploadservice.ServerResponse;
import net.gotev.uploadservice.UploadInfo;
import net.gotev.uploadservice.UploadNotificationConfig;
import net.gotev.uploadservice.UploadStatusDelegate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.UUID;

public class Background extends Service implements SensorEventListener {

    private SensorManager mSensorManager;
    Bundle extras;
    Intent i;
    RecordSaveData recordSaveData1;
    RealTimeController realTimeController;
    Handler handler;
    ZipManager zipManager;
    String UPLOAD_URL;

    ArrayList<String> csvnames;
    FileObserver fileObservercsv;
    FileObserver fileObserverzip;

    boolean compressionflag = false;
    boolean append = false;
    int iappendctr = 0;
    final int limitappend = 1;
    int sec;

    long StartTime;
    String time;

    String fileName;

    String ipaddress;

    String longitude;
    String latitutde;
    String compass;
    String location;

    private LocationManager locationManager;
    private LocationListener locationListener;

    Runnable runnable;
    long resettime=0;

    Boolean mystart = true;
    Boolean myexit = false;
    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {
        locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                longitude = String.valueOf(location.getLongitude());
                latitutde = String.valueOf(location.getLatitude());
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {

            }

            @Override
            public void onProviderEnabled(String s) {

            }

            @Override
            public void onProviderDisabled(String s) {
                Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        };
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 0, locationListener);


    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
        //region ---------Initialization ------------------
        StartTime = SystemClock.uptimeMillis();
        ipaddress = intent.getStringExtra("ipaddress");

        //Toast.makeText(getApplication(), ipaddress, Toast.LENGTH_SHORT).show();
        //Toast.makeText(getApplication(), intent.getStringExtra("location"), Toast.LENGTH_SHORT).show();
        location = intent.getStringExtra("location");
        //Toast.makeText(getApplication(), intent.getStringExtra("device"), Toast.LENGTH_SHORT).show();
        csvnames = new ArrayList<>();
        zipManager = new ZipManager();
        extras = new Bundle();
        i = new Intent();
        recordSaveData1 = new RecordSaveData();
        realTimeController = new RealTimeController();
        handler = new Handler();
        //endregion
        Toast.makeText(getApplication(), "Services Enabled", Toast.LENGTH_SHORT).show();
        //region ---------------------Register Listeners for Sensors( Accelerometer / Orientation) Temporarily
        mSensorManager = (SensorManager) getSystemService(Activity.SENSOR_SERVICE);
        mSensorManager.registerListener(this, mSensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0), SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mSensorManager.getSensorList(Sensor.TYPE_ORIENTATION).get(0), SensorManager.SENSOR_DELAY_GAME);
        //endregion
        //region ------------------- Set up for Delay / Start Up --------------------
        Calendar settime1 = Calendar.getInstance();
        sec = (60 - settime1.get(Calendar.SECOND)) * 1000;
        resettime = SystemClock.uptimeMillis();
        //endregion

        //region ---------------------(HANDLER) Special Delay Call (Infinite Loop in an definite delay)--------------------
        Calendar setnamedate1 = Calendar.getInstance();
        fileName = setnamedate1.get(Calendar.YEAR) + "-" + (setnamedate1.get(Calendar.MONTH)+1)  + "-" + setnamedate1.get(Calendar.DATE)  + "-" + setnamedate1.get(Calendar.HOUR_OF_DAY)  + "-" + setnamedate1.get(Calendar.MINUTE)  + "-" + setnamedate1.get(Calendar.SECOND)  + ".csv";
        csvnames.add(fileName);
                runnable = new Runnable() {
                    @Override
                    public void run() {

                        //somechanges---

                        Calendar setnamedate = Calendar.getInstance();
                        fileName = setnamedate.get(Calendar.YEAR) + "-" + (setnamedate.get(Calendar.MONTH)+1)  + "-" + setnamedate.get(Calendar.DATE)  + "-" + setnamedate.get(Calendar.HOUR_OF_DAY)  + "-" + setnamedate.get(Calendar.MINUTE)  + "-" + setnamedate.get(Calendar.SECOND)  + ".csv";
                        csvnames.add(fileName);
                        //------------------ Initialize Delay for the next Call -----------------
                        Calendar settime = Calendar.getInstance();
                        sec = (60 - settime.get(Calendar.SECOND)) * 1000; // seconds delay for minute
                        myexit = true;
                        // ----------------- Recursive Call --------------------------
                        handler.postDelayed(this, sec);
                    }
                };
                handler.postDelayed(runnable, sec); // calling handler for infinite loop
        //endregion

        //region --------- FileObserver for Compression -------
       final String csvpath = android.os.Environment.getExternalStorageDirectory().toString() + "/Samples/";
       fileObservercsv = new FileObserver(csvpath,FileObserver.ALL_EVENTS) {
           @Override
           public void onEvent(int event, final String file) {
               if (event == FileObserver.CLOSE_WRITE && compressionflag) {
                  // Log.d("MediaListenerService", "File created [" + csvpath + file + "]");
                   new Handler(Looper.getMainLooper()).post(new Runnable() {
                       @Override
                       public void run() {
                          // Toast.makeText(getBaseContext(), file + " was saved!", Toast.LENGTH_SHORT).show();
                           zipManager.compressGzipFile("Samples/" + file,  file + ".gz");  // Compressing Data
                            compressionflag = false;
                       }
                   });
               }
           }
       };
       fileObservercsv.startWatching();
        //endregion

        //region  -------- FileObserver for Sending Data to Database -------------
        final String zippath = android.os.Environment.getExternalStorageDirectory().toString() + "/Zip/";
        fileObserverzip = new FileObserver(zippath,FileObserver.ALL_EVENTS) {
            @Override
            public void onEvent(int event, final String file) {
                if (event == FileObserver.CREATE) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                           // Toast.makeText(getBaseContext(), file + " was compressed!", Toast.LENGTH_SHORT).show();
                            for(int ictr=0 ; ictr<csvnames.size()-1 ; ictr++) {
                               uploadMultipart("/storage/emulated/0/Zip/" + csvnames.get(ictr) + ".gz", csvnames.get(ictr),ictr);
                             }

                        }
                    });
                }
            }
        };
        fileObserverzip.startWatching();
        //endregion


        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
        Toast.makeText(this,"Service Stopped",Toast.LENGTH_SHORT).show();
        handler.removeCallbacks(runnable);
        if(locationManager != null){
            locationManager.removeUpdates(locationListener);
        }

    }

    FileOutputStream fos;
    BufferedWriter bw;
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            time = ""+(SystemClock.uptimeMillis()-resettime);

            realTimeController.updateXYZ(sensorEvent.values[0], sensorEvent.values[1], sensorEvent.values[2]);
           // recordSaveData1.recordData(realTimeController.getX(), realTimeController.getY(), realTimeController.getZ(), time);

            try {
                if(mystart){
                    File myDir = new File("storage/emulated/0/Samples");
                    if(!myDir.exists())
                    {
                        myDir.mkdirs();
                    }
                    File file = new File(myDir,fileName);
                    file.createNewFile();
                    fos = new FileOutputStream(file);
                    bw = new BufferedWriter(new OutputStreamWriter(fos));
                    bw.write("ARRIVALS,,,,\r\n");
                    bw.write("#sitename,,,,\r\n");
                    bw.write("#onset,,,,\r\n");
                    bw.write("#first motion,,,,\r\n");
                    bw.write("#phase,,,,\r\n");
                    bw.write("#year month day,,,,\r\n");
                    bw.write("#hour minute,,,,\r\n");
                    bw.write("#second,,,,\r\n");
                    bw.write("#uncertainty in seconds,,,,\r\n");
                    bw.write("#peak amplitude,,,,\r\n");
                    bw.write("#frequency at P phase,,,,\r\n");
                    bw.write(",,,,\r\n");
                    bw.write("longitude : " + longitude + "\r\n");
                    bw.write("latitude : " + latitutde + "\r\n");
                    bw.write("compass : " + compass + "\r\n");
                    bw.write("TIME SERIES,,,,\r\n");
                    bw.write("LLPS,LLPS,LLPS,#sitename,\r\n");
                    bw.write("EHE _,EHN _,EHZ _,#component,\r\n");
                    bw.write(0 + "," + 0 + "," + 0 + ",#authority,\r\n");
                    String[] separated = fileName.split("[-|.]");
                    String year = separated[0];
                    String month = separated[1];
                    String day = separated[2];
                    String hour = separated[3];
                    String minute = separated[4];
                    String second = separated[5];
                    String hold = year;
                    hold = Integer.parseInt(month) <= 9 ? hold + "0" + Integer.parseInt(month) : hold + Integer.parseInt(month);
                    hold = Integer.parseInt(day) <= 9 ? hold + "0" + Integer.parseInt(day) : "" + hold + Integer.parseInt(day);
                    bw.write(hold + "," + hold + "," + hold + ",#year month day,\r\n");
                    hold = hour;
                    hold = Integer.parseInt(minute) <= 9 ? hold + "0" + Integer.parseInt(minute) : "" + hold + Integer.parseInt(minute);
                    bw.write(hold + "," + hold + "," + hold + ",#hour minute,\r\n");
                    bw.write(second + "," + second + "," + second + ",#second,\r\n");
                    bw.write("0,0,0,#sync,\r\n");
                    bw.write(",,,#sync source,\r\n");
                    bw.write("g,g,g,g,\r\n");
                    bw.write("--------,--------,--------,,\r\n");
                    Toast.makeText(this, "has been started", Toast.LENGTH_SHORT).show();
                    mystart = false;
                }
                bw.write(realTimeController.getX()+","+realTimeController.getY()+","+realTimeController.getZ()+"\r\n");
                if(myexit) {
                    compressionflag = true;
                    bw.write("       ,       ,       ,,\r\n" +
                            "       ,       ,       ,,\r\n" +
                            "END,END,END,,\r\n");
                    bw.flush();
                    bw.close();
                    fos.flush();
                    fos.close();
                    myexit = false;
                    mystart = true;
                    Toast.makeText(this, "has been saved", Toast.LENGTH_SHORT).show();
                }
            }catch (Exception e){
                e.printStackTrace();
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            i.putExtra("valueX", String.valueOf(realTimeController.getX()));
            i.putExtra("valueY", String.valueOf(realTimeController.getY()));
            i.putExtra("valueZ", String.valueOf(realTimeController.getZ()));
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            compass = String.valueOf(sensorEvent.values[0]);
            i.putExtra("compass", String.valueOf(sensorEvent.values[0]));
        }
        i.setAction("FILTER");
        sendBroadcast(i);

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void uploadMultipart(String path, final String name, final int index) {
        //getting name for the image
        UPLOAD_URL = "http://"+ipaddress+"/data/api/uploaddata.php";
        //String name=(currentTime.getYear()+1900)+"-"+(currentTime.getMonth()+1)+"-"+currentTime.getDate()+"-"+currentTime.getHours()+currentTime.getMinutes()+"-"+currentTime.getSeconds()+".csv";
        String[] separated = name.split("[-|.]");
        String year = separated[0];
        String month = separated[1];
        String day = separated[2];
        String hour = separated[3];
        String minute = separated[4];

        //getting the actual path of the image
        //  String path = FilePath.getPath(getActivity(), filePath);

        if (path == null) {

            Toast.makeText(this, "NULL PATH", Toast.LENGTH_LONG).show();
        } else {
            //Uploading code

            try {
                final String uploadId = UUID.randomUUID().toString();


                //Creating a multi part request
                new MultipartUploadRequest(getApplicationContext(), uploadId, UPLOAD_URL)
                        .addFileToUpload(path, "gz") //Adding file
                        .addParameter("name", name) //Adding text parameter to the request
                        .addParameter("location", location)
                        .addParameter("month", month)
                        .addParameter("day", day)
                        .addParameter("year", year)
                        .addParameter("hour", hour)
                        .addParameter("minute", minute)
                        .setNotificationConfig(new UploadNotificationConfig())
                        .setMaxRetries(2)
                        .setDelegate(new UploadStatusDelegate() {
                            @Override
                            public void onProgress(Context context, UploadInfo uploadInfo) {
                                Toast.makeText(getApplicationContext(), "Uploading to Server", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(Context context, UploadInfo uploadInfo, ServerResponse serverResponse, Exception exception) {
                                Toast.makeText(getApplicationContext(), "Server Connection Failed", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onCompleted(Context context, UploadInfo uploadInfo, ServerResponse serverResponse) {
                                Toast.makeText(getApplicationContext(), serverResponse.getBodyAsString(), Toast.LENGTH_SHORT).show();
                                if(serverResponse.getBodyAsString().equals("Successfully Uploaded yehey")) {
                                    File file1 = new File("/storage/emulated/0/Samples/", name);
                                    boolean deleted1 = file1.delete();
                                    File file2 = new File("/storage/emulated/0/Zip/", name + ".gz");
                                    boolean deleted2 = file2.delete();
                                    csvnames.remove(index);
                                }

                            }

                            @Override
                            public void onCancelled(Context context, UploadInfo uploadInfo) {
                                Toast.makeText(getApplicationContext(), "Uploading Cancelled", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .startUpload(); //Starting the upload

            } catch (Exception exc) {
                Toast.makeText(getApplicationContext(), exc.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}

