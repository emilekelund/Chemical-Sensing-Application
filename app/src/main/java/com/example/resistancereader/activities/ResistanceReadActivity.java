package com.example.resistancereader.activities;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.example.resistancereader.BleServices.BleResistanceService;
import com.example.resistancereader.R;
import com.example.resistancereader.utilities.MsgUtils;

public class ResistanceReadActivity extends Activity {

    private BluetoothDevice mSelectedDevice = null;
    private TextView mResistanceView;
    private TextView mDeviceView;
    private TextView mStatusView;
    private String mDeviceAddress;

    private BleResistanceService mBluetoothService;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resistance_read);

        // Setup UI references
        mResistanceView = findViewById(R.id.resistanceValueViewer);
        mDeviceView = findViewById(R.id.device_view);
        mStatusView = findViewById(R.id.status_view);


        final Intent intent = getIntent();
        mSelectedDevice = intent.getParcelableExtra(ScanActivity.SELECTED_DEVICE);

        if (mSelectedDevice == null) {
            MsgUtils.createDialog("Error", "No device found", this).show();
            mDeviceView.setText(R.string.no_resistance_board);
        } else {
            mDeviceView.setText(mSelectedDevice.getName());
        }
    }


}
