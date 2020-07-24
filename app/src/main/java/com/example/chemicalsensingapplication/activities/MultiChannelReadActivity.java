package com.example.chemicalsensingapplication.activities;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.example.chemicalsensingapplication.R;
import com.example.chemicalsensingapplication.services.BleService;
import com.example.chemicalsensingapplication.services.GattActions;
import com.example.chemicalsensingapplication.utilities.MsgUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Objects;

import static com.example.chemicalsensingapplication.services.GattActions.ACTION_GATT_CHEMICAL_SENSING_EVENTS;
import static com.example.chemicalsensingapplication.services.GattActions.EVENT;
import static com.example.chemicalsensingapplication.services.GattActions.MULTICHANNEL_DATA;
import static com.example.chemicalsensingapplication.services.GattActions.POTENTIOMETRIC_DATA;

public class MultiChannelReadActivity extends AppCompatActivity {
    private static final String TAG = MultiChannelReadActivity.class.getSimpleName();
    public static String SELECTED_DEVICE = "Selected device";

    private BluetoothDevice mSelectedDevice = null;
    private String mDeviceAddress;
    private BleService mBluetoothLeService;
    private TextView mDeviceView;
    private TextView mStatusView;
    private ToggleButton mSaveDataButton;
    private ToggleButton mPauseDataButton;
    private TextView we1;
    private TextView we2;
    private TextView we3;
    private TextView we4;
    private TextView we5;
    private TextView we6;
    private TextView we7;

    private static final DateFormat df = new SimpleDateFormat("yyMMdd_HH:mm"); // Custom date format for file saving
    private FileOutputStream dataSample = null;

    private static final float MULTIPLIER = 0.03125F;

    private long timeSinceSamplingStart = 0;

    private final CountDownTimer mCountDownTimer = new
            CountDownTimer(86400000, 50) {
                @Override
                public void onTick(long millisUntilFinished) {
                    timeSinceSamplingStart = 86400000 - millisUntilFinished;
                }

                @Override
                public void onFinish() {

                }
            };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multichannel_read);

        we1 = findViewById(R.id.we1_value);
        we2 = findViewById(R.id.we2_value);
        we3 = findViewById(R.id.we3_value);
        we4 = findViewById(R.id.we4_value);
        we5 = findViewById(R.id.we5_value);
        we6 = findViewById(R.id.we6_value);
        we7 = findViewById(R.id.we7_value);
        mDeviceView = findViewById(R.id.device_view);
        mStatusView = findViewById(R.id.status_view);
        mSaveDataButton = findViewById(R.id.savedata_toggle);
        mPauseDataButton = findViewById(R.id.pause_toggle);

        // SETTING UP THE TOOLBAR
        Toolbar mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Multichannel");
        // TOOLBAR: BACK ARROW
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        // On-click listener for the toggle button used to sample data
        mSaveDataButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // Button is checked, create a new file and start the timer
                    dataSample = createFiles();
                    mCountDownTimer.start();
                    MsgUtils.showToast("Data saving started", getApplicationContext());
                } else {
                    try {
                        // Button is unchecked, close the file
                        closeFiles(dataSample);
                        MsgUtils.showToast("Data is now stored on your phone.", getApplicationContext());
                        mCountDownTimer.cancel();
                        timeSinceSamplingStart = 0;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        final Intent intent = getIntent();
        mSelectedDevice = intent.getParcelableExtra(ScanActivity.SELECTED_DEVICE);

        if (mSelectedDevice == null) {
            MsgUtils.createDialog("Error", "No device found", this).show();
            mDeviceView.setText(R.string.no_potentiometric_board);
        } else {
            mDeviceView.setText(mSelectedDevice.getName());
            mDeviceAddress = mSelectedDevice.getAddress();
        }

        // Bind to BleImuService
        // We use onResume or onStart to register a broadcastReceiver
        Intent gattServiceIntent = new Intent(this, BleService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        isStoragePermissionGranted(); // The user needs to approve the file storing


    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
        // When the activity is paused, toggle the button so that the files are closed
        if (mSaveDataButton.isChecked()) {
            mSaveDataButton.toggle();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    /*
    Callback methods to manage the (BleImu)Service lifecycle.
    */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BleService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.i(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
            Log.i(TAG, "onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
            Log.i(TAG, "onServiceDisconnected");
        }
    };

    /*
    A BroadcastReceiver handling various events fired by the Service, see GattActions.Event.
    */
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ACTION_GATT_CHEMICAL_SENSING_EVENTS.equals(action)) {
                GattActions.Event event = (GattActions.Event) intent.getSerializableExtra(EVENT);
                if (event != null) {
                    switch (event) {
                        case GATT_CONNECTED:
                            we1.setText("-");
                            we2.setText("-");
                            we3.setText("-");
                            we4.setText("-");
                            we5.setText("-");
                            we6.setText("-");
                            we7.setText("-");
                            break;
                        case GATT_DISCONNECTED:
                            mStatusView.setText(event.toString());
                            break;
                        case GATT_SERVICES_DISCOVERED:
                            break;
                        case MULTICHANNEL_SERVICE_DISCOVERED:
                            mStatusView.setText(event.toString());
                            break;
                        case DATA_AVAILABLE:
                            final double[] rawPotentials = intent.getDoubleArrayExtra(MULTICHANNEL_DATA);
                            assert rawPotentials != null;
                            double[] potentials = new double[rawPotentials.length];

                            for (int i = 0; i < potentials.length; i++) {
                                potentials[i] = potentials[i] * MULTIPLIER;
                            }

                            we1.setText(String.format("%.1fmV", potentials[1]));
                            we2.setText(String.format("%.1fmV", potentials[2]));
                            we3.setText(String.format("%.1fmV", potentials[3]));
                            we4.setText(String.format("%.1fmV", potentials[4]));
                            we5.setText(String.format("%.1fmV", potentials[5]));
                            we6.setText(String.format("%.1fmV", potentials[6]));
                            we7.setText(String.format("%.1fmV", potentials[7]));

                            break;
                        case MULTICHANNEL_SERVICE_NOT_AVAILABLE:
                            mStatusView.setText(event.toString());
                            break;
                        default:
                            mStatusView.setText(R.string.device_unreachable);
                    }
                }
            }
        }
    };

    // Intent filter for broadcast updates from BleService
    private IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_GATT_CHEMICAL_SENSING_EVENTS);
        return intentFilter;
    }

    // Method to sample data used by the ToggleButton
    private FileOutputStream createFiles() {
        // Get the external storage location
        String root = Environment.getExternalStorageDirectory().toString();
        // Create a new directory
        File myDir = new File(root, "/Chemical_sensing_data");
        if (!myDir.exists()) {
            myDir.mkdirs();
        }

        String potentiometric = "multiChannel_measurement_" + df.format(Calendar.getInstance().getTime()) + ".csv";

        File potentiometricFile = new File(myDir, potentiometric);

        try {
            potentiometricFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            return new FileOutputStream(potentiometricFile, true);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }

    }

    // Helper method to close the files.
    private static void closeFiles(FileOutputStream fo) throws IOException {
        fo.flush();
        fo.close();
    }

    // Method to check if the user has granted access to store data on external memory
    public boolean isStoragePermissionGranted() {
        String TAG = "Storage Permission";
        if (Build.VERSION.SDK_INT >= 23) {
            if (this.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Permission is granted");
                return true;
            } else {
                Log.i(TAG, "Permission is revoked");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        } else { //permission is automatically granted on sdk<23 upon installation
            Log.i(TAG,"Permission is granted");
            return true;
        }
    }
}
