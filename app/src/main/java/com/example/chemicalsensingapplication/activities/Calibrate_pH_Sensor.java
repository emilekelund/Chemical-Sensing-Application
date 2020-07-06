package com.example.chemicalsensingapplication.activities;

import android.bluetooth.BluetoothDevice;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.chemicalsensingapplication.R;
import com.example.chemicalsensingapplication.services.BleService;

import java.util.Objects;

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

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibrate_ph);

        mPotentialView = findViewById(R.id.potentialValueViewer);
        mDeviceView = findViewById(R.id.device_view);
        mStatusView = findViewById(R.id.status_view);

        // SETTING UP THE TOOLBAR
        Toolbar mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Potentiometric measurements");
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


    }

}
