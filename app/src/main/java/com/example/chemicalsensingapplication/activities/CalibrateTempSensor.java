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
import java.util.Objects;

import static com.example.chemicalsensingapplication.services.GattActions.ACTION_GATT_CHEMICAL_SENSING_EVENTS;
import static com.example.chemicalsensingapplication.services.GattActions.EVENT;
import static com.example.chemicalsensingapplication.services.GattActions.POTENTIOMETRIC_DATA;
import static com.example.chemicalsensingapplication.services.GattActions.TEMPERATURE_DATA;

public class CalibrateTempSensor extends AppCompatActivity {
    private static final String TAG = CalibrateTempSensor.class.getSimpleName();

    private BluetoothDevice mSelectedDevice = null;
    private TextView mStatusView;
    private TextView mDeviceView;
    private TextView mResistanceView;
    private String mDeviceAddress;
    private BleService mBluetoothLeService;
    private TextView temp1_box;
    private TextView temp2_box;
    private TextView temp3_box;
    private TextView temp4_box;
    private TextView temp5_box;
    private TextView resistance1_box;
    private TextView resistance2_box;
    private TextView resistance3_box;
    private TextView resistance4_box;
    private TextView resistance5_box;
    private TextView regressionFormula;
    private static float slope;
    private static float intercept;

    private ExponentialMovingAverage ewmaFilter = new ExponentialMovingAverage(0.04);

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibrate_temp);

        mResistanceView = findViewById(R.id.resistance_view);
        mDeviceView = findViewById(R.id.device_view);
        mStatusView = findViewById(R.id.status_view);
        temp1_box = findViewById(R.id.temp1);
        temp2_box = findViewById(R.id.temp2);
        temp3_box = findViewById(R.id.temp3);
        temp4_box = findViewById(R.id.temp4);
        temp5_box = findViewById(R.id.temp5);
        resistance1_box = findViewById(R.id.resistance1);
        resistance2_box = findViewById(R.id.resistance2);
        resistance3_box = findViewById(R.id.resistance3);
        resistance4_box = findViewById(R.id.resistance4);
        resistance5_box = findViewById(R.id.resistance5);
        regressionFormula = findViewById(R.id.regression_formula);

        // SETTING UP THE TOOLBAR
        Toolbar mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Temperature calibration");
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

        slope = TemperatureReadActivity.getSlope();
        intercept = TemperatureReadActivity.getIntercept();

        if ((slope != 0) && (intercept != 0)) {
            regressionFormula.setText(String.format("f(x) = %.2fx + %.2f", slope, intercept));
        }

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
                            mResistanceView.setText(R.string.board_disconnected);
                        case GATT_SERVICES_DISCOVERED:
                            mStatusView.setText(event.toString());
                        case TEMPERATURE_SERVICE_DISCOVERED:
                            mStatusView.setText(event.toString());
                            break;
                        case DATA_AVAILABLE:
                            mStatusView.setText(R.string.ready_to_calibrate);
                            final double resistance = intent.getDoubleExtra(TEMPERATURE_DATA, 0);
                            double ewmaResistance;
                            ewmaResistance = ewmaFilter.average(resistance);

                            mResistanceView.setText(String.format("%.1fk\u2126", (ewmaResistance * (1 * Math.pow(10, -3))))); // Display in kiloOhm

                            break;
                        case TEMPERATURE_SERVICE_NOT_AVAILABLE:
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

    public void calibrateTemp(View view) {
        float temp1, temp2, temp3, temp4, temp5, resistance1, resistance2, resistance3, resistance4, resistance5;
        String temp1String = temp1_box.getText().toString();
        String temp2String = temp2_box.getText().toString();
        String temp3String = temp3_box.getText().toString();
        String temp4String = temp4_box.getText().toString();
        String temp5String = temp5_box.getText().toString();
        String resistance1String = resistance1_box.getText().toString();
        String resistance2String = resistance2_box.getText().toString();
        String resistance3String = resistance3_box.getText().toString();
        String resistance4String = resistance4_box.getText().toString();
        String resistance5String = resistance5_box.getText().toString();

        if (temp1String.length() == 0 || temp2String.length() == 0 || temp3String.length() == 0 ||
                temp4String.length() == 0 || temp5String.length() == 0 || resistance1String.length() == 0 ||
                resistance2String.length() == 0 || resistance3String.length() == 0 || resistance4String.length() == 0 ||
                resistance5String.length() == 0) {

            MsgUtils.showToast("Please enter values in all boxes", this);

        } else {

            FileOutputStream calibrationValues = createFiles();
            temp1 = Float.parseFloat(temp1String);
            temp2 = Float.parseFloat(temp2String);
            temp3 = Float.parseFloat(temp3String);
            temp4 = Float.parseFloat(temp4String);
            temp5 = Float.parseFloat(temp5String);
            resistance1 = Float.parseFloat(resistance1String);
            resistance2 = Float.parseFloat(resistance2String);
            resistance3 = Float.parseFloat(resistance3String);
            resistance4 = Float.parseFloat(resistance4String);
            resistance5 = Float.parseFloat(resistance5String);

            float a = 5 * ((resistance1 * temp1) + (resistance2 * temp2) + (resistance3 * temp3) +
                    (resistance4 * temp4) + (resistance5 * temp5));

            float b = (resistance1 + resistance2 + resistance3 + resistance4 + resistance5) *
                    (temp1 + temp2 + temp3 + temp4 + temp5);

            float c = (float) (5 * (Math.pow(resistance1, 2) + Math.pow(resistance2, 2) + Math.pow(resistance3, 2) +
                                Math.pow(resistance4, 2) + Math.pow(resistance5, 2)));

            float d = (float) Math.pow(resistance1 + resistance2 + resistance3 + resistance4 + resistance5, 2);

            slope = (a - b) / (c - d);

            float e = (temp1 + temp2 + temp3 + temp4 + temp5);
            float f = slope * (resistance1 + resistance2 + resistance3 + resistance4 + resistance5);

            intercept = (e - f) / 5;

            hideKeyboard(view);

            try {
                calibrationValues.write((slope + ",").getBytes());
                calibrationValues.write((intercept + "\n").getBytes());
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            regressionFormula.setText(String.format("f(x) = %.2fx + %.2f", slope, intercept));

            try {
                closeFiles(calibrationValues);
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            MsgUtils.showToast("Success! Saved calibration.", this);

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

        String pH_calibration = "temp_calibrations.csv";

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

    private void hideKeyboard(View v) {
        InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(v.getApplicationWindowToken(),0);
    }
}
