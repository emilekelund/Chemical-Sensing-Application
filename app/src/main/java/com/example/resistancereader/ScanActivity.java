package com.example.resistancereader;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;

public class ScanActivity extends AppCompatActivity {

    public static final String RESISTANCE = "Resistance";

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private ArrayList<BluetoothDevice> mDeviceList;
    private BtDeviceAdapter mBtDeviceAdapter;
    private TextView mScanInfoView;

    private static final long SCAN_PERIOD = 10000; // represented in milliseconds

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
}
