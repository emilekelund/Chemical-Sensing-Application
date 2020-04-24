package com.example.resistancereader;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES;
import static com.example.resistancereader.BleServices.ResistanceBoardUUIDs.RESISTANCE_SERVICE;
import static com.example.resistancereader.utils.MsgUtils.showToast;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ScanActivity extends AppCompatActivity {

    private static final String TAG = ScanActivity.class.getSimpleName();

    public static final String RESISTANCE = "Resistance";

    public static final int REQUEST_ENABLE_BT = 1000;
    public static final int REQUEST_ACCESS_LOCATION = 1001;

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private ArrayList<BluetoothDevice> mDeviceList;
    private BtDeviceAdapter mBtDeviceAdapter;
    private TextView mScanInfoView;

    private static final long SCAN_PERIOD = 10000; // represented in milliseconds

    // We are only interested in devices with our service, so we create a scan filter
    private static final List<ScanFilter> RESISTANCE_SCAN_FILTER;
    private static final ScanSettings SCAN_SETTINGS;

    static {
        ScanFilter resistanceServiceFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(RESISTANCE_SERVICE))
                .build();
        RESISTANCE_SCAN_FILTER = new ArrayList<>();
        RESISTANCE_SCAN_FILTER.add(resistanceServiceFilter);
        SCAN_SETTINGS = new ScanSettings.Builder()
                .setScanMode(CALLBACK_TYPE_ALL_MATCHES).build();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        mHandler = new Handler();
        mDeviceList = new ArrayList<>();

        //Setup of view and button
        mScanInfoView = findViewById(R.id.scan_info);
        Button startScanButton = findViewById(R.id.start_scan_button);
        startScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDeviceList.clear();
            }
        });

        // Setup recycler view and corresponding adapter
        RecyclerView recyclerView = findViewById(R.id.scan_recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        mBtDeviceAdapter = new BtDeviceAdapter(mDeviceList,
                new BtDeviceAdapter.IOnItemSelectedCallBack() {
                    @Override
                    public void onItemClicked(int position) {

                    }
                });

        recyclerView.setAdapter(mBtDeviceAdapter);
    }

    // Check BLE permissions and turn on BT (if turned off) - user interaction(s)
    private void initBLE() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            showToast("BLE is not supported", this);
            finish();
        } else {
            showToast("BLE is supported", this);
            // Access Location is a "dangerous" permission
            int hasAccessLocation = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION);
            if (hasAccessLocation != PackageManager.PERMISSION_GRANTED) {
                // ask the user for permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_ACCESS_LOCATION);
                // the callback method onRequestPermissionsResult gets the result of this request
            }
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // turn on BT
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    // Start scanning for BLE devices
    private void startScanning(List<ScanFilter> scanFilters,
                               ScanSettings scanSettings,
                               long scanPeriod) {
        final BluetoothLeScanner scanner =
                mBluetoothAdapter.getBluetoothLeScanner();

        if (!mScanning) {
            // stop the scanning after a pre-defined scan period
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mScanning) {
                        mScanning = false;
                        scanner.stopScan(mScanCallback);
                        showToast("BLE scan stopped", this);
                    }
                }
            }, scanPeriod);

            mScanning = true;
            scanner.startScan(scanFilters, scanSettings, mScanCallback);
            mScanInfoView.setText(R.string.no_devices_found);
            showToast("BLE scan started", this);
        }

    }

    // Stop scanning for Ble devices
    private void stopScanning() {
        if (mScanning) {
            BluetoothLeScanner scanner =
                    mBluetoothAdapter.getBluetoothLeScanner();

            scanner.stopScan(mScanCallback);
            mScanning = false;
            showToast("BLE scan stopped", this);
        }
    }

    // Callback methods for the BluetoothLeScanner
    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.i(TAG, "onScanResult");
            final BluetoothDevice device = result.getDevice();

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!mDeviceList.contains(device)) {
                        mDeviceList.add(device);
                        mBtDeviceAdapter.notifyDataSetChanged();
                        String info = "Found " + mDeviceList.size() + " device(s)\n"
                                + "Touch to connect";
                        mScanInfoView.setText(info);
                    }
                }
            });
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.i(TAG, "onScanFailed");
        }
    };

}
