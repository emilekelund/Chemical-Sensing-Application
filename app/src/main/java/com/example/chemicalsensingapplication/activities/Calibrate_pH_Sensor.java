package com.example.chemicalsensingapplication.activities;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.chemicalsensingapplication.R;
import com.example.chemicalsensingapplication.services.BleService;
import com.example.chemicalsensingapplication.services.GattActions;
import com.example.chemicalsensingapplication.utilities.ExponentialMovingAverage;
import com.example.chemicalsensingapplication.utilities.MsgUtils;

import java.io.IOException;
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

    private ExponentialMovingAverage ewmaFilter = new ExponentialMovingAverage(0.1);
    private static final float MULTIPLIER = 0.03125F;

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
        float pH4Potential = 0;
        float pH7Potential = 0;
        float pH10Potential = 0;
        String ph4String = pH4_box.getText().toString();
        String ph7String = pH7_box.getText().toString();
        String ph10String = pH10_box.getText().toString();

        if (ph4String.length() == 0 || ph7String.length() == 0 || ph10String.length() == 0) {
            MsgUtils.showToast("Please enter values in all boxes", this);
        } else {
            pH4Potential = Float.parseFloat(ph4String);
            pH7Potential = Float.parseFloat(ph7String);
            pH10Potential = Float.parseFloat(ph10String);
        }


    }
}
