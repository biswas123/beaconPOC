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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity implements BeaconConsumer {

    private static final int PERMISSION_ALL = 10;

    private BeaconManager beaconManager;
    private BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

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
    private String URL = "http://62.232.248.77:4053/api/beaconInfo/create.php";

    String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "OnCreate() called.");
        setContentView(R.layout.activity_main);

        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        } else {
            checkGPSStatus();
            getUserLocation();
            setDeviceUUID();
        }

        verifyBluetooth();
        this.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        this.registerReceiver(mReceiver, new IntentFilter(LocationManager.MODE_CHANGED_ACTION));
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
            beaconManager.bind(this);
            Log.i(TAG, "Beacon service just got bound");
            //backgroundPowerSaver = new BackgroundPowerSaver(this);
        }
        mRecyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(mRecyclerView.getContext(),
                DividerItemDecoration.VERTICAL);
        mRecyclerView.addItemDecoration(dividerItemDecoration);
        mRecyclerView.setHasFixedSize(true);
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mAdapter = new MyAdapter(beaconInfoList);
        mRecyclerView.setAdapter(mAdapter);

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
                // Toast.makeText(getApplicationContext(), "I see you at lat: " + latitude + " long: " + longitude, Toast.LENGTH_SHORT).show();
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


    RangeNotifier rangeNotifier = new RangeNotifier() {
        public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
            for (Beacon beacon : beacons) {
                if (beacon.getDistance() < 5.0) {

                    try {
                        beaconManager.stopRangingBeaconsInRegion(region);

                        BeaconInfo beaconInfo = new BeaconInfo(beacon.getId1().toString(), Integer.parseInt(beacon.getId2().toString()), Integer.parseInt(beacon.getId3().toString()), beacon.getDistance());
                        beaconInfoList.add(beaconInfo);

                        mAdapter.notifyDataSetChanged();

                        OkHttpHandler okHttpHandler = new OkHttpHandler(MainActivity.this);

                        try {

                            JSONObject obj = new JSONObject();
                            obj.put("deviceId", deviceUUID);
                            obj.put("deviceTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime()));
                            obj.put("uuid", beacon.getId1());
                            obj.put("majorValue", beacon.getId2());
                            obj.put("minorValue", beacon.getId3());
                            obj.put("latitude", latitude);
                            obj.put("longitude", longitude);

                            String request = obj.toString();
                            okHttpHandler.execute(URL, request);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    MonitorNotifier monitorNotifier = new MonitorNotifier() {
        @Override
        public void didEnterRegion(Region region) {
            Log.i(TAG, "I just saw an beacon for the first time!");
            playSoundAndVibrate(true);
            try {
                Log.i(TAG, "Started Ranging");
                beaconManager.startRangingBeaconsInRegion(new Region("uniqueId1", null, null, null));
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            Toast.makeText(MainActivity.this, "I see a Beacon entered.", Toast.LENGTH_LONG).show();

        }

        @Override
        public void didExitRegion(Region region) {
            Log.i(TAG, "I no longer see an beacon");
            playSoundAndVibrate(false);
            try {
                beaconManager.stopRangingBeaconsInRegion(region);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            Toast.makeText(MainActivity.this, "I see a Beacon exited.", Toast.LENGTH_LONG).show();

        }

        @Override
        public void didDetermineStateForRegion(int state, Region region) {

        }
    };

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
        beaconManager.addMonitorNotifier(monitorNotifier);

        try {
            stopRangingAndMonitoring();
            Log.i(TAG, "Started Monitoring.");
            beaconManager.startMonitoringBeaconsInRegion(new Region("uniqueId2", null, null, null));
        } catch (RemoteException e) {
            Log.i(TAG, e.toString());
        }
    }

    private void stopRangingAndMonitoring() {
        for (Region region : beaconManager.getMonitoredRegions()) {
            try {
                beaconManager.stopMonitoringBeaconsInRegion(region);
                Log.i(TAG, "Stop monitoring: " + region.toString());
            } catch (RemoteException e) {
            }
        }
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
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
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
    protected void onDestroy() {
        super.onDestroy();
        stopRangingAndMonitoring();
        beaconManager.unbind(this);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(10001);
        beaconManager.disableForegroundServiceScanning();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();
        if (id == R.id.exit) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


}
