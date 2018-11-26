package com.example.beaconpoc.beaconpoc;

import android.Manifest;

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
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.Collection;
import java.util.Iterator;

public class MainActivity extends AppCompatActivity implements BeaconConsumer {

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private BeaconManager beaconManager;
    private BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

    private final String TAG = "com.example.beaconpoc";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access.");
                builder.setMessage("Please grant location access so this app can detect beacons.");

                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            } else {
                checkGPSStatus();
            }
        } else {
            checkGPSStatus();
        }
        verifyBluetooth();
        this.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        this.registerReceiver(mReceiver, new IntentFilter(LocationManager.MODE_CHANGED_ACTION));


        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

        Notification.Builder builder = new Notification.Builder(MainActivity.this);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentTitle("Scanning for Beacons");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(TAG,
                    "BeaconPOC", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("BeaconPOC");
            NotificationManager notificationManager = (NotificationManager) getSystemService(
                    Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);

            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentIntent(contentIntent);
            builder.setChannelId(channel.getId());
        }
        beaconManager.enableForegroundServiceScanning(builder.build(), 456);
        beaconManager.setEnableScheduledScanJobs(false);
        beaconManager.setRegionStatePersistenceEnabled(false);
        beaconManager.bind(this);

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

            // It means the user has changed his bluetooth state.
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

    @Override
    public void onBeaconServiceConnect() {

        final RangeNotifier rangeNotifier = new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                for (Beacon beacon : beacons) {
                    if (beacon.getDistance() < 3.0) {
                        Toast.makeText(MainActivity.this, "I see a beacon that is less than 5 meters away. " + beacon.getId1() + ":" + beacon.getId2() + ":" + beacon.getId3() + ":" + beacon.getDistance(), Toast.LENGTH_SHORT).show();
                        try {
                            beaconManager.stopRangingBeaconsInRegion(region);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        // Perform distance-specific action here
                    }
                }
            }
        };


        beaconManager.addMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
                Log.i(TAG, "I just saw an beacon for the first time!");
                MediaPlayer mp = MediaPlayer.create(getApplicationContext(), R.raw.on);// the song is a filename which i have pasted inside a folder **raw** created under the **res** folder.//
                mp.start();

                try {
                    beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
                    beaconManager.addRangeNotifier(rangeNotifier);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                Toast.makeText(MainActivity.this, "I see a Beacon entered.", Toast.LENGTH_LONG).show();

            }

            @Override
            public void didExitRegion(Region region) {
                Log.i(TAG, "I no longer see an beacon");
                MediaPlayer mp = MediaPlayer.create(getApplicationContext(), R.raw.off);// the song is a filename which i have pasted inside a folder **raw** created under the **res** folder.//
                mp.start();
                try {
                    beaconManager.stopRangingBeaconsInRegion(region);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                Toast.makeText(MainActivity.this, "I see a Beacon exited.", Toast.LENGTH_LONG).show();

            }

            @Override
            public void didDetermineStateForRegion(int state, Region region) {
                Log.i(TAG, "I have just switched from seeing/not seeing beacons: " + state);

            }
        });

        try {
            beaconManager.startMonitoringBeaconsInRegion(new Region("myMonitoringUniqueId", null, null, null));
        } catch (RemoteException e) {
            Log.d(TAG, e.toString());
        }


    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "coarse location permission granted");
                    checkGPSStatus();
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.unbind(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.exit) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


}
