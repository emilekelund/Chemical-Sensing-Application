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
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
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
import com.example.chemicalsensingapplication.utilities.ExponentialMovingAverage;
import com.example.chemicalsensingapplication.utilities.MsgUtils;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Objects;

import static com.example.chemicalsensingapplication.services.GattActions.ACTION_GATT_CHEMICAL_SENSING_EVENTS;
import static com.example.chemicalsensingapplication.services.GattActions.EVENT;
import static com.example.chemicalsensingapplication.services.GattActions.POTENTIOMETRIC_DATA;

public class PotentiometricReadActivity extends AppCompatActivity {
    private static final String TAG = PotentiometricReadActivity.class.getSimpleName();
    public static String SELECTED_DEVICE = "Selected device";

    private BluetoothDevice mSelectedDevice = null;
    private TextView mPotentialView;
    private TextView m_pHView;
    private TextView mDeviceView;
    private TextView mStatusView;
    private String mDeviceAddress;
    private BleService mBluetoothLeService;
    private ToggleButton mSaveDataButton;
    private static float slope = 0;
    private static float intercept = 0;

    private ExponentialMovingAverage ewmaFilter = new ExponentialMovingAverage(0.1);

    private static final DateFormat df = new SimpleDateFormat("yyMMdd_HH:mm"); // Custom date format for file saving
    private FileOutputStream dataSample = null;

    private static final float MULTIPLIER = 0.03125F;

    private ILineDataSet set = null;
    private LineChart mChart;
    private Thread thread;
    private boolean plotData = true;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_potentiometric_read);

        mPotentialView = findViewById(R.id.potentialValueViewer);
        m_pHView = findViewById(R.id.pHViewer);
        mDeviceView = findViewById(R.id.device_view);
        mStatusView = findViewById(R.id.status_view);
        mSaveDataButton = findViewById(R.id.toggleButton);

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

        // On-click listener for the toggle button used to sample data
        mSaveDataButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // Button is checked, create a new file and start the timer
                    dataSample = createFiles();
                } else {
                    try {
                        // Button is unchecked, close the file
                        closeFiles(dataSample);
                        MsgUtils.showToast("Data is now stored on your phone.", getApplicationContext());
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

        // Setup UI reference to the chart
        mChart = findViewById(R.id.pHChart);

        // enable description text
        mChart.getDescription().setEnabled(false);

        // enable touch gestures
        mChart.setTouchEnabled(true);

        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(true);

        // set an alternative background color
        mChart.setBackgroundColor(Color.WHITE);

        LineData data = new LineData();
        data.setValueTextColor(Color.BLACK);

        // add empty data
        mChart.setData(data);

        // get the legend (only possible after setting data)
        Legend l = mChart.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTextColor(Color.BLACK);

        // X-axis setup
        XAxis bottomAxis = mChart.getXAxis();
        bottomAxis.setTextColor(Color.BLACK);
        bottomAxis.setDrawGridLines(true);
        bottomAxis.setPosition(XAxis.XAxisPosition.BOTTOM_INSIDE);
        bottomAxis.setAvoidFirstLastClipping(true);
        bottomAxis.setEnabled(true);

        // Y-axis setup
        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setDrawGridLines(true);
        leftAxis.setAxisMaximum(15f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(true);
        // Disable right Y-axis
        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);

        mChart.getAxisLeft().setDrawGridLines(true);
        mChart.getXAxis().setDrawGridLines(false);
        mChart.setDrawBorders(false);

        feedMultiple();


        // Bind to BleImuService
        // We use onResume or onStart to register a broadcastReceiver
        Intent gattServiceIntent = new Intent(this, BleService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        isStoragePermissionGranted(); // The user needs to approve the file storing

        // Read the latest calibrations
        try {
            readCalibrationData();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
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
        // Read the latest calibrations
        try {
            readCalibrationData();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
        if (thread != null) {
            thread.interrupt();
        }
        // When the activity is paused, toggle the button so that the files are closed
        if (mSaveDataButton.isChecked()) {
            mSaveDataButton.toggle();
        }
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
        thread.interrupt();
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
    A method to add our pH entries to the chart
     */
    private void addEntry(double pH) {
        LineData data = mChart.getData();


        if (data != null) {
            set = data.getDataSetByIndex(0);
            // set.addEntry(.....); Can be called as well
        }

        if (set == null) {
            set = createSet();
            assert data != null;
            data.addDataSet(set);
        }

        assert data != null;
        data.addEntry(new Entry(set.getEntryCount(), (float) pH), 0);
        data.notifyDataChanged();

        // let the chart know it's data has changed
        mChart.notifyDataSetChanged();

        // limit the number of visible entries
        mChart.setVisibleXRangeMaximum(50);
        //mChart.setVisibleYRange(0,30, YAxis.AxisDependency.LEFT);

        // move to the latest entry
        //mChart.moveViewToX(data.getEntryCount());
        mChart.moveViewTo(data.getEntryCount(), (float) pH, YAxis.AxisDependency.LEFT);
    }

    private LineDataSet createSet() {

        LineDataSet set = new LineDataSet(null, "pH Value");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setLineWidth(3f);
        set.setColor(Color.RED);
        set.setHighlightEnabled(false);
        set.setDrawValues(false);
        set.setDrawCircles(true);
        set.setCircleRadius(2f);
        return set;
    }

    private void feedMultiple() {

        if (thread != null) {
            thread.interrupt();
        }

        thread = new Thread(new Runnable() {

            @Override
            public void run() {
                while (true) {
                    plotData = true;
                    try {
                        Thread.sleep(900);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        thread.start();
    }


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
                        case GATT_DISCONNECTED:
                            mPotentialView.setText(R.string.board_disconnected);
                        case GATT_SERVICES_DISCOVERED:
                        case POTENTIOMETRIC_SERVICE_DISCOVERED:
                            mStatusView.setText(event.toString());
                            break;
                        case DATA_AVAILABLE:
                            final double rawPotential = intent.getDoubleExtra(POTENTIOMETRIC_DATA, 0);
                            float potential = (float) (rawPotential * MULTIPLIER);
                            float ewmaPotential = (float) ewmaFilter.average(potential);
                            float pH = potentialTo_pH(ewmaPotential);
                            Log.i(TAG, "Potential: " + potential);
                            Log.i(TAG, "pH: " + pH);
                            mPotentialView.setText(String.format("%.2f mV", ewmaPotential));
                            m_pHView.setText(String.format("pH %.1f", pH));

                            if (plotData) {
                                addEntry(pH);
                                plotData = false;
                            }

                            if (mSaveDataButton.isChecked()) {
                                try {
                                    dataSample.write((potential + ",").getBytes());
                                    dataSample.write((pH + "\n").getBytes());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

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

    private float potentialTo_pH(float potential) {
        return (slope * potential) + intercept;
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

        String potentiometric = "pH_measurement_" + df.format(Calendar.getInstance().getTime()) + ".csv";

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

    public void startCalibration(View view) {
        Intent intent = new Intent(PotentiometricReadActivity.this, Calibrate_pH_Sensor.class);
        intent.putExtra(SELECTED_DEVICE, mSelectedDevice);
        startActivity(intent);
    }

    public void readCalibrationData() throws FileNotFoundException {
        String root = Environment.getExternalStorageDirectory().toString() + "/Chemical_sensing_data/Calibrations";
        InputStream calibrationData = new FileInputStream(root + "/pH_calibrations.csv");

        BufferedReader reader = new BufferedReader(new InputStreamReader(calibrationData));

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] rowData = line.split(",");
                slope = Float.parseFloat(rowData[0]);
                intercept = Float.parseFloat(rowData[1]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            try {
                calibrationData.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public static float getSlope() {
        return slope;
    }

    public static float getIntercept() {
        return intercept;
    }
}
