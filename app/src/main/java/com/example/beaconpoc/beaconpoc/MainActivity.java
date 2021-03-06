package com.example.beaconpoc.beaconpoc;

import android.Manifest;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.example.beaconpoc.beaconpoc.OkHttpHandler.JSON;

public class MainActivity extends AppCompatActivity implements BeaconConsumer {

    private static final int PERMISSION_ALL = 10;
    private static final long BEACONS_RETRRIEVE_TIME_IN_SECONDS = 5 * 60 * 1000L;


    private final int FOREGROUND_NOTIFICATION_ID = 10001;
    private final String TAG = "beaconpoc.log";

    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private MyAdapter mAdapter;
    private BackgroundPowerSaver backgroundPowerSaver;

    private double longitude;
    private double latitude;
    private String deviceUUID;
    private List<BeaconInfo> beaconInfoList = new ArrayList<>();

    private BeaconManager beaconManager;
    private BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

    private String URL = "http://62.232.248.77:4053/api/";
    //private String URL = "http://192.168.200.54:80/BeaconPOCApi/api/";

    private SharedPreferences sharedpreferences;


    private static final double DEFAULT_DISTANCE_THRESHOLD = 5.0;
    private HashMap<String, String> beaconMap = null;
    String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
    };

    private Timer pollTimer = null;
    private ConcurrentHashMap<Beacon, String> seenBeacons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        seenBeacons = new ConcurrentHashMap<Beacon, String>();

        SharedPreferences prefs = getSharedPreferences("APIURL", MODE_PRIVATE);
        String APIUrl = prefs.getString("APIUrl", null);
        if (APIUrl == null) {
            sharedpreferences = getSharedPreferences("APIURL", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedpreferences.edit();
            editor.putString("APIUrl", URL);
            editor.commit();
        }

        Log.i(TAG, "OnCreate() called.");
        setContentView(R.layout.activity_main);

        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        this.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        this.registerReceiver(mReceiver, new IntentFilter(LocationManager.MODE_CHANGED_ACTION));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        } else {
            checkGPSStatus();
            getUserLocation();
            setDeviceUUID();
        }

        verifyBluetooth();
        mRecyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(mRecyclerView.getContext(),
                DividerItemDecoration.VERTICAL);
        mRecyclerView.addItemDecoration(dividerItemDecoration);
        mRecyclerView.setHasFixedSize(true);
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mAdapter = new MyAdapter(beaconInfoList);
        mRecyclerView.setAdapter(mAdapter);


        if (!beaconManager.isBound(this)) {
            Notification.Builder builder = new Notification.Builder(MainActivity.this);

            builder.setSmallIcon(R.mipmap.ic_launcher);

            builder.setContentTitle("Scanning for Beacons");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(TAG,
                        "BeaconPOC", NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("BeaconPOC");
                NotificationManager notificationManager = (NotificationManager) getSystemService(
                        Context.NOTIFICATION_SERVICE);
                notificationManager.createNotificationChannel(channel);

                PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

                builder.setContentIntent(contentIntent);
                builder.setChannelId(channel.getId());
            }
            beaconManager.enableForegroundServiceScanning(builder.build(), FOREGROUND_NOTIFICATION_ID);
            beaconManager.setEnableScheduledScanJobs(false);
            beaconManager.setRegionStatePersistenceEnabled(false);

            // set the duration of the scan to be 5.1 seconds
           // beaconManager.setForegroundScanPeriod(5100l);
            // set the time between each scan to be 5 seconds
        //    beaconManager.setForegroundBetweenScanPeriod(5000l);
            beaconManager.setForegroundScanPeriod(2100l);
            beaconManager.setForegroundBetweenScanPeriod(2000l);

            Log.i(TAG, "Beacon service just got bound");
        }

        pollRegisteredBeacons();

    }

    private void pollRegisteredBeacons() {
        pollTimer = new Timer();
        pollTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                retrieveRegisteredBeacons();
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this, "Getting registered beacons...", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, 0, BEACONS_RETRRIEVE_TIME_IN_SECONDS);
    }

    private void retrieveRegisteredBeacons() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONArray beacons = getBeacons();

                if (beacons != null && beacons.length() > 0) {
                    beaconMap = new HashMap<>();
                    for (int i = 0; i < beacons.length(); i++) {
                        try {
                            JSONObject beacon = new JSONObject(beacons.get(i).toString());
                            String beaconUUID = beacon.getString("uuid");
                            String beaconName = beacon.getString("name");
                            String beaconThreshold = beacon.getString("distanceThreshold");
                            if (beaconThreshold.equals("null") || beaconThreshold.isEmpty()) {
                                beaconThreshold = Double.toString(DEFAULT_DISTANCE_THRESHOLD);
                            }
                            beaconMap.put(beaconUUID, beaconName + "###" + beaconThreshold);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(MainActivity.this, "No registered beacons retrieved...", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                if (!beaconManager.isBound(MainActivity.this)) {
                    beaconManager.bind(MainActivity.this);
                }
            }
        }).start();
    }

    private void getUserLocation() {

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        @SuppressLint("MissingPermission")
        Location location = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        if (location != null) {

            longitude = location.getLongitude();
            latitude = location.getLatitude();
        }
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                longitude = location.getLongitude();
                latitude = location.getLatitude();
                //Toast.makeText(getApplicationContext(), "I see you at lat: " + latitude + " long: " + longitude, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {

            }

            @Override
            public void onProviderEnabled(String s) {

            }

            @Override
            public void onProviderDisabled(String s) {

            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 15000, 10, locationListener);

    }

    private void showURLConfigurationDialog() {

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.custom_dialog, null);
        dialogBuilder.setView(dialogView);

        final EditText edt = (EditText) dialogView.findViewById(R.id.configureURL);
        SharedPreferences prefs = getSharedPreferences("APIURL", MODE_PRIVATE);
        String APIUrl = prefs.getString("APIUrl", null);
        edt.setText(APIUrl);
        final TextView tv = (TextView) dialogView.findViewById(R.id.pingResponse);
        dialogBuilder.setTitle("Configuration");
        dialogBuilder.setMessage("Enter API URL below:");
        dialogBuilder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                final String url = edt.getText().toString();
                if (!url.isEmpty()) {
                    sharedpreferences = getSharedPreferences("APIURL", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedpreferences.edit();
                    editor.putString("APIUrl", url);
                    editor.commit();
                    Toast.makeText(MainActivity.this, "Update successful.", Toast.LENGTH_LONG).show();
                }
            }
        });
        dialogBuilder.setNegativeButton("Ping server", null);
        AlertDialog b = dialogBuilder.create();
        b.show();
        b.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String url = edt.getText().toString();
                tv.setText("Pinging server ...");
                if (!url.isEmpty()) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            String response = pingURL(url);
                            if (response != null && response.equals("OK")) {
                                tv.setText("Connection successfully established.");
                            } else {
                                tv.setText("Unable to connect to server.");
                            }
                        }
                    }).start();
                }
            }
        });
    }

    private void verifyBluetooth() {

        try {
            if (!BeaconManager.getInstanceForApplication(this).checkAvailability()) {
                BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (!mBluetoothAdapter.isEnabled()) {
                    mBluetoothAdapter.enable();
                }
            }
        } catch (RuntimeException e) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Bluetooth LE not available");
            builder.setMessage("Sorry, this device does not support Bluetooth LE.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();

        }

    }

    public void checkGPSStatus() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();
        }
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(i);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();


            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {

                if (btAdapter.getState() == BluetoothAdapter.STATE_OFF) {
                    Snackbar.make(findViewById(R.id.snack), "Please turn on your bluetooth to make sure beacons are detected.", Snackbar.LENGTH_LONG).show();
                    return;
                }

            } else if (action.equals(LocationManager.MODE_CHANGED_ACTION)) {
                final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

                if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    Snackbar.make(findViewById(R.id.snack), "Please turn on your GPS to make sure beacons are detected.", Snackbar.LENGTH_LONG).show();
                    return;
                }
            }
        }
    };


    @SuppressLint("MissingPermission")
    public void setDeviceUUID() {
        TelephonyManager tManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        if (tManager != null) {
            deviceUUID = tManager.getDeviceId();
        }
    }

    public void cleanSeenBeacons(Collection<Beacon> beacons) {
        for (Beacon seenBeacon : seenBeacons.keySet()) {
            if (!beacons.contains(seenBeacon)) {
                seenBeacons.remove(seenBeacon);

                Log.i(TAG, "One beacon removed after instant dissapearance." + seenBeacon.getId1());
            }
        }
    }

    RangeNotifier rangeNotifier = new RangeNotifier() {
        public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
            Log.i(TAG, "Did range called");

            if (beacons.size() > 0) {
                cleanSeenBeacons(beacons);
                for (Beacon beacon : beacons) {
                    String beaconUUID = beacon.getId1().toString();
                    String beaconName = getNameFromUUID(beaconUUID) != null ? getNameFromUUID(beaconUUID) : "Unregistered beacon";
                    double beaconDistanceThreshold = getThresholdDistanceFromUUID(beaconUUID) != null ? Double.parseDouble(getThresholdDistanceFromUUID(beaconUUID)) : DEFAULT_DISTANCE_THRESHOLD;
                    double beaconDistance = beacon.getDistance();
                    int beaconMajor = Integer.parseInt(beacon.getId2().toString());
                    int beaconMinor = Integer.parseInt(beacon.getId3().toString());

                    if (beaconDistance <= beaconDistanceThreshold) {
                        if (!seenBeacons.containsKey(beacon)) {
                            String foundTime = new Date(System.currentTimeMillis()).toString();
                            seenBeacons.put(beacon, foundTime);

                            BeaconInfo beaconInfo = new BeaconInfo(beaconUUID, beaconMajor, beaconMinor, beaconDistance, beaconName, foundTime);
                            beaconInfoList.add(0, beaconInfo);

                            mAdapter.notifyDataSetChanged();
                            if (!beaconName.equals("Unregistered beacon")) {
                                playSoundAndVibrate(true);
                            }
                            try {
                                OkHttpHandler okHttpHandler = new OkHttpHandler(MainActivity.this);
                                JSONObject obj = new JSONObject();
                                obj.put("deviceId", deviceUUID);
                                obj.put("deviceTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime()));
                                obj.put("uuid", beacon.getId1());
                                obj.put("majorValue", beacon.getId2());
                                obj.put("minorValue", beacon.getId3());
                                obj.put("latitude", latitude);
                                obj.put("longitude", longitude);
                                obj.put("name", beaconName);

                                String request = obj.toString();

                                SharedPreferences prefs = getSharedPreferences("APIURL", MODE_PRIVATE);
                                String APIUrl = prefs.getString("APIUrl", null);

                                okHttpHandler.execute(APIUrl + "beaconInfo/create.php", request);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        if (seenBeacons.containsKey(beacon)) {
                            seenBeacons.remove(beacon);
                            Toast.makeText(MainActivity.this, "A beacon crossed its distance threshold.", Toast.LENGTH_LONG).show();
                            Log.i(TAG, "One beacon removed due to crossing distance more than threshold" + beacon.getId1());
                        }
                    }
                }
            } else {
                seenBeacons.clear();
                Toast.makeText(MainActivity.this, "No beacons around.", Toast.LENGTH_LONG).show();
                Log.i(TAG, "All seen beacons cleared");
            }

        }
    };


    /* RangeNotifier rangeNotifier = new RangeNotifier() {
         public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
             Log.i(TAG, "Did range called");

             if (beacons.size() > 0) {
                 cleanSeenBeacons(beacons);
                 for (Beacon beacon : beacons) {
                     if (beacon.getDistance() < DISTANCE_THRESHOLD) {

                         if (!seenBeacons.containsKey(beacon)) {

                             Date foundTime = new Date(System.currentTimeMillis());
                             seenBeacons.put(beacon, foundTime);

                             OkHttpHandler okHttpHandler = new OkHttpHandler(MainActivity.this);


                             String beaconName = getNameFromUUID(beacon.getId1().toString());

                             if (beaconName.isEmpty()) {
                                 beaconName = "Unregistered beacon";
                             } else {
                                 playSoundAndVibrate(true);
                             }

                             BeaconInfo beaconInfo = new BeaconInfo(beacon.getId1().toString(), Integer.parseInt(beacon.getId2().toString()), Integer.parseInt(beacon.getId3().toString()), beacon.getDistance(), beaconName, foundTime.toString());
                             beaconInfoList.add(beaconInfo);
                             Collections.reverse(beaconInfoList);

                             mAdapter.notifyDataSetChanged();

                             try {

                                 JSONObject obj = new JSONObject();
                                 obj.put("deviceId", deviceUUID);
                                 obj.put("deviceTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime()));
                                 obj.put("uuid", beacon.getId1());
                                 obj.put("majorValue", beacon.getId2());
                                 obj.put("minorValue", beacon.getId3());
                                 obj.put("latitude", latitude);
                                 obj.put("longitude", longitude);
                                 obj.put("name", beaconName);

                                 String request = obj.toString();

                                 SharedPreferences prefs = getSharedPreferences("APIURL", MODE_PRIVATE);
                                 String APIUrl = prefs.getString("APIUrl", null);

                                 okHttpHandler.execute(APIUrl + "beaconInfo/create.php", request);
                             } catch (Exception e) {
                                 e.printStackTrace();
                             }

                         }
                     } else {
                         if (seenBeacons.containsKey(beacon)) {
                             seenBeacons.remove(beacon);
                             Toast.makeText(MainActivity.this, "A beacon crossed distance threshold.", Toast.LENGTH_LONG).show();
                             Log.i(TAG, "One beacon removed due to crossing distance more than threshold" + beacon.getId1());
                         }
                     }
                 }
             } else {
                 seenBeacons.clear();
                 Toast.makeText(MainActivity.this, "No beacons around.", Toast.LENGTH_LONG).show();
                 Log.i(TAG, "All seen beacons cleared");
             }

         }
     };
 */
    private String getNameFromUUID(String uuid) {

        if (beaconMap != null && beaconMap.size() > 0) {
            if (beaconMap.containsKey(uuid)) {
                String name_thresholdDistance = beaconMap.get(uuid);
                return name_thresholdDistance.split("###")[0];
            }
        }

        return null;
    }

    private String getThresholdDistanceFromUUID(String uuid) {

        if (beaconMap != null && beaconMap.size() > 0) {
            if (beaconMap.containsKey(uuid)) {
                String name_thresholdDistance = beaconMap.get(uuid);
                return name_thresholdDistance.split("###")[1];
            }
        }

        return null;
    }

    private JSONArray getBeacons() {
        OkHttpClient client = new OkHttpClient();
        SharedPreferences prefs = getSharedPreferences("APIURL", MODE_PRIVATE);
        String APIUrl = prefs.getString("APIUrl", null);
        String url = APIUrl + "beacon/read.php";

        JSONObject jsonObj = new JSONObject();
        String jsonString = jsonObj.toString();

        RequestBody body = RequestBody.create(JSON, jsonString);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            JSONObject data = new JSONObject(response.body().string());
            if (data.has("Status") && data.get("Status").equals("200")) {
                return new JSONArray(data.getString("Message"));
            }
            return null;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    private String pingURL(String APIUrl) {
        OkHttpClient client = new OkHttpClient();

        String url = APIUrl + "ping.php";
        JSONObject jsonObj = new JSONObject();
        String jsonString = jsonObj.toString();

        RequestBody body = RequestBody.create(JSON, jsonString);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            JSONObject data = new JSONObject(response.body().string());
            if (data.has("Status") && data.get("Status").equals("200")) {
                return data.get("Message").toString();
            }
            return null;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void playSoundAndVibrate(boolean enter) {

        int sound = enter ? R.raw.on : R.raw.off;
        MediaPlayer mp = MediaPlayer.create(getApplicationContext(), sound);
        mp.start();

        if (enter) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(200);
            }
        }
    }


    @Override
    public void onBeaconServiceConnect() {
        Log.i(TAG, "onBeaconServiceConnect() : Connected.");

        beaconManager.addRangeNotifier(rangeNotifier);

        try {
            stopRanging();
            Log.i(TAG, "Started Ranging");
            beaconManager.startRangingBeaconsInRegion(new Region("uniqueId1", null, null, null));

        } catch (RemoteException e) {
            Log.i(TAG, e.toString());
        }
    }

    private void stopRanging() {

        for (Region region : beaconManager.getRangedRegions()) {
            try {
                beaconManager.stopRangingBeaconsInRegion(region);
                Log.i(TAG, "Stop ranging: " + region.toString());
            } catch (RemoteException e) {
            }
        }
    }


    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
                                           int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_ALL:

                checkGPSStatus();
                getUserLocation();
                setDeviceUUID();
                break;

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        stopRanging();
        if (pollTimer != null) {
            pollTimer.cancel();
        }
        beaconManager.removeAllMonitorNotifiers();
        beaconManager.removeAllRangeNotifiers();
        beaconManager.unbind(this);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(FOREGROUND_NOTIFICATION_ID);
        beaconManager.disableForegroundServiceScanning();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();
        if (id == R.id.exit) {
            finish();
            return true;
        } else if (id == R.id.configure) {

            showURLConfigurationDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


}
