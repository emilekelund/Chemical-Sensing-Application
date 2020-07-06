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
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.example.chemicalsensingapplication.R;
import com.example.chemicalsensingapplication.services.BleService;
import com.example.chemicalsensingapplication.services.GattActions;
import com.example.chemicalsensingapplication.utilities.ExponentialMovingAverage;
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
import static com.example.chemicalsensingapplication.services.GattActions.POTENTIOMETRIC_DATA;

public class Calibrate_pH_Sensor extends AppCompatActivity {
    private static final String TAG = Calibrate_pH_Sensor.class.getSimpleName();

    private BluetoothDevice mSelectedDevice = null;
    private TextView mPotentialView;
    private TextView mStatusView;
    private TextView mDeviceView;
    private String mDeviceAddress;
    private BleService mBluetoothLeService;
    private TextView pH4_box;
    private TextView pH7_box;
    private TextView pH10_box;
    private TextView newEquation;
    private float[] eqValues = new float[2];

    private ExponentialMovingAverage ewmaFilter = new ExponentialMovingAverage(0.1);
    private static final float MULTIPLIER = 0.03125F;

    private static final DateFormat df = new SimpleDateFormat("yyMMdd_HH:mm:ss"); // Custom date format for file saving

    private FileOutputStream calibrationValues = null;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibrate_ph);

        mPotentialView = findViewById(R.id.potential_view);
        mDeviceView = findViewById(R.id.device_view);
        mStatusView = findViewById(R.id.status_view);
        pH4_box = findViewById(R.id.pH4_box);
        pH7_box = findViewById(R.id.pH7_box);
        pH10_box = findViewById(R.id.pH10_box);
        newEquation = findViewById(R.id.new_equation);

        // SETTING UP THE TOOLBAR
        Toolbar mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("pH calibration");
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

        isStoragePermissionGranted();

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
    }

    /*
    NB! Unbind from service when this activity is destroyed (the service itself
    might then stop).
     */
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
                            mStatusView.setText(event.toString());
                        case GATT_DISCONNECTED:
                            mPotentialView.setText(R.string.board_disconnected);
                        case GATT_SERVICES_DISCOVERED:
                            mStatusView.setText(event.toString());
                        case POTENTIOMETRIC_SERVICE_DISCOVERED:
                            mStatusView.setText(event.toString());
                            break;
                        case DATA_AVAILABLE:
                            mStatusView.setText(R.string.ready_to_calibrate);
                            final double rawPotential = intent.getDoubleExtra(POTENTIOMETRIC_DATA, 0);
                            float potential = (float) (rawPotential * MULTIPLIER);
                            float ewmaPotential = (float) ewmaFilter.average(potential);
                            Log.i(TAG, "Potential: " + ewmaPotential);
                            mPotentialView.setText(String.format("%.2f mV", ewmaPotential));

                            break;
                        case POTENTIOMETRIC_SERVICE_NOT_AVAILABLE:
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

    public void startCalibration(View view) {
        float pH4Potential;
        float pH7Potential;
        float pH10Potential;
        String ph4String = pH4_box.getText().toString();
        String ph7String = pH7_box.getText().toString();
        String ph10String = pH10_box.getText().toString();

        if (ph4String.length() == 0 || ph7String.length() == 0 || ph10String.length() == 0) {
            MsgUtils.showToast("Please enter values in all boxes", this);

        } else {
            calibrationValues = createFiles();

            pH4Potential = Float.parseFloat(ph4String);
            pH7Potential = Float.parseFloat(ph7String);
            pH10Potential = Float.parseFloat(ph10String);

            float a = 3 * ((pH4Potential * 4) + (pH7Potential * 7) + pH10Potential * 10);
            float b = (pH4Potential + pH7Potential + pH10Potential) * (4 + 7 + 10);
            float c = (float) (3 * (Math.pow(pH4Potential, 2) + Math.pow(pH7Potential, 2) + Math.pow(pH10Potential, 2)));
            float d = (float) Math.pow((pH4Potential + pH7Potential + pH10Potential), 2);

            float slope = (a - b) / (c - d);

            float e = 4 + 7 + 10;
            float f = slope * (pH4Potential + pH7Potential + pH10Potential);

            float intercept = (e - f) / 3;

            eqValues[0] = slope;
            eqValues[1] = intercept;
            MsgUtils.showToast("Success!", this);

            hideKeybaord(view);

            try {
                calibrationValues.write((eqValues[0] + ",").getBytes());
                calibrationValues.write((eqValues[1] + "\n").getBytes());
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            newEquation.setText(String.format("f(x) = %.2fx + %.2f", eqValues[0], eqValues[1]));

            try {
                closeFiles(calibrationValues);
            } catch (IOException ex) {
                ex.printStackTrace();
            }

        }

    }

    // Method to save the last calibration values
    private FileOutputStream createFiles() {
        // Get the external storage location
        String root = Environment.getExternalStorageDirectory().toString();
        // Create a new directory
        File myDir = new File(root, "/Chemical_sensing_data/Calibrations");
        if (!myDir.exists()) {
            myDir.mkdirs();
        }

        String pH_calibration = "pH_calibrations.txt";

        File potentiometricFile = new File(myDir, pH_calibration);

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
    private void hideKeybaord(View v) {
        InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(v.getApplicationWindowToken(),0);
    }


}
