package com.example.chemicalsensingapplication.activities;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.chemicalsensingapplication.services.BleService;

public class MultiChannelReadActivity extends AppCompatActivity {
    private static final String TAG = MultiChannelReadActivity.class.getSimpleName();
    public static String SELECTED_DEVICE = "Selected device";

    private BluetoothDevice mSelectedDevice = null;
    private String mDeviceAddress;
    private BleService mBluetoothLeService;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
