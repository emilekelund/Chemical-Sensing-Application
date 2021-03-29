package com.example.chemicalsensingapplication.activities;

import android.Manifest;
import android.annotation.SuppressLint;
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
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

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

    private BluetoothDevice mSelectedDevice = null;
    private String mDeviceAddress;
    private BleService mBluetoothLeService;
    private TextView mDeviceView;
    private TextView mStatusView;
    private ToggleButton mSaveDataButton;
    private ToggleButton mPauseDataButton;
    private TextView[] wePotentials = new TextView[7];
    private TextView noOfChannels;
    private static int activeChannels = 1;
    private static int entryCount = 0;

    private static final DateFormat df = new SimpleDateFormat("yyMMdd_HH:mm"); // Custom date format for file saving
    private FileOutputStream dataSample = null;

    private static final float MULTIPLIER = 0.03125F;

    private ILineDataSet[] sets = new ILineDataSet[7];
    private LineChart mChart;
    private Thread thread;
    private boolean plotData = true;

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

        wePotentials[0] = findViewById(R.id.we1_value);
        wePotentials[1] = findViewById(R.id.we2_value);
        wePotentials[2] = findViewById(R.id.we3_value);
        wePotentials[3] = findViewById(R.id.we4_value);
        wePotentials[4] = findViewById(R.id.we5_value);
        wePotentials[5] = findViewById(R.id.we6_value);
        wePotentials[6] = findViewById(R.id.we7_value);
        noOfChannels = findViewById(R.id.numberofchannels);
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
                    try {
                        dataSample.write(("Time[s],WE1,WE2,WE3,WE4,WE5,WE6,WE7\n").getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
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

        // Setup UI reference to the chart
        mChart = findViewById(R.id.multichannel_chart);

        // enable description text
        mChart.getDescription().setEnabled(false);

        // enable touch gestures
        mChart.setTouchEnabled(true);

        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(false);

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
        leftAxis.setAxisMaximum(1000f);
        leftAxis.setAxisMinimum(-1000f);
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
    private void addEntry(double potential, int index) {
        LineData data = mChart.getData();

        if (data != null) {
            for (int i = 0; i < 7; i++) {
                sets[i] = data.getDataSetByIndex(i);
            }
        }


        if (sets[1] == null && sets[2] == null && sets[3] == null
                && sets[4] == null && sets[5] == null && sets[6] == null) {
            sets = createSets();
            for (int i = 0; i < 7; i++) {
                data.addDataSet(sets[i]);
            }

        }


        data.addEntry(new Entry(entryCount, (float) potential), index);

        // let the chart know it's data has changed
        mChart.notifyDataSetChanged();

        // limit the number of visible entries
        mChart.setVisibleXRangeMaximum(1000);
        //mChart.setVisibleYRange(0,30, YAxis.AxisDependency.LEFT);

        // move to the latest entry
        //mChart.moveViewToX(data.getEntryCount());
        mChart.moveViewTo(data.getEntryCount(), (float) potential, YAxis.AxisDependency.LEFT);
    }

    private LineDataSet[] createSets() {

        LineDataSet[] dataSets = new LineDataSet[7];
        dataSets[0] = new LineDataSet(null, "WE1");
        dataSets[1] = new LineDataSet(null, "WE2");
        dataSets[2] = new LineDataSet(null, "WE3");
        dataSets[3] = new LineDataSet(null, "WE4");
        dataSets[4] = new LineDataSet(null, "WE5");
        dataSets[5] = new LineDataSet(null, "WE6");
        dataSets[6] = new LineDataSet(null, "WE7");

        for (LineDataSet dataSet : dataSets) {
            dataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
            dataSet.setHighlightEnabled(false);
            dataSet.setDrawCircles(false);
            dataSet.setDrawValues(false);
            dataSet.setCircleRadius(2.5f);
            //dataSet.enableDashedLine(0, 1, 0);
        }

        dataSets[0].setColor(Color.RED);
        dataSets[1].setColor(Color.BLACK);
        dataSets[2].setColor(Color.BLUE);
        dataSets[3].setColor(Color.MAGENTA);
        dataSets[4].setColor(Color.GREEN);
        dataSets[5].setColor(Color.YELLOW);
        dataSets[6].setColor(Color.GRAY);
        dataSets[0].setCircleColor(Color.RED);
        dataSets[1].setCircleColor(Color.BLACK);
        dataSets[2].setCircleColor(Color.BLUE);
        dataSets[3].setCircleColor(Color.MAGENTA);
        dataSets[4].setCircleColor(Color.GREEN);
        dataSets[5].setCircleColor(Color.YELLOW);
        dataSets[6].setCircleColor(Color.GRAY);
        dataSets[0].setCircleHoleColor(Color.RED);
        dataSets[1].setCircleHoleColor(Color.BLACK);
        dataSets[2].setCircleHoleColor(Color.BLUE);
        dataSets[3].setCircleHoleColor(Color.MAGENTA);
        dataSets[4].setCircleHoleColor(Color.GREEN);
        dataSets[5].setCircleHoleColor(Color.YELLOW);
        dataSets[6].setCircleHoleColor(Color.GRAY);

        return dataSets;
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
        @SuppressLint("DefaultLocale")
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ACTION_GATT_CHEMICAL_SENSING_EVENTS.equals(action)) {
                GattActions.Event event = (GattActions.Event) intent.getSerializableExtra(EVENT);
                if (event != null) {
                    switch (event) {
                        case GATT_CONNECTED:
                            for (TextView wePotential : wePotentials) {
                                wePotential.setText("-");
                            }
                            break;
                        case GATT_DISCONNECTED:
                        case MULTICHANNEL_SERVICE_NOT_AVAILABLE:
                            mStatusView.setText(event.toString());
                            break;
                        case GATT_SERVICES_DISCOVERED:
                            break;
                        case MULTICHANNEL_SERVICE_DISCOVERED:
                            mStatusView.setText(event.toString());
                            for (TextView wePotential : wePotentials) {
                                wePotential.setText(R.string.not_active);
                            }
                            break;
                        case DATA_AVAILABLE:
                            final int[] rawPotentials = intent.getIntArrayExtra(MULTICHANNEL_DATA);
                            assert rawPotentials != null;
                            double[] potentials = new double[rawPotentials.length];

                            for (int i = 0; i < potentials.length; i++) {
                                potentials[i] = rawPotentials[i] * MULTIPLIER;
                            }

                            for (int i = 0; i < activeChannels; i++) {
                                wePotentials[i].setText(String.format("%.1fmV", potentials[i]));
                            }

                            if (plotData) {
                                for (int i = 0; i < activeChannels; i++) {
                                    addEntry(potentials[i], i);
                                }
                                plotData = false;
                                entryCount++;
                            }

                            if (mSaveDataButton.isChecked() && !mPauseDataButton.isChecked()) {
                                try {
                                    dataSample.write(((float) timeSinceSamplingStart / 1000f + ",").getBytes());
                                    for (int i = 0; i < activeChannels; i++) {
                                        dataSample.write(((float) potentials[i] + ",").getBytes());
                                    }
                                    for (int i = activeChannels; i < 7; i++) {
                                        dataSample.write(("-").getBytes());
                                        if (i < 6) {
                                            dataSample.write((",").getBytes());
                                        }
                                    }
                                    dataSample.write(("\n").getBytes());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

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

        String potentiometric = "MultiChannel_measurement_" + df.format(Calendar.getInstance().getTime()) + ".csv";

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
            Log.i(TAG, "Permission is granted");
            return true;
        }
    }

    public void setChannels(View view) {
        int fromTextBox;
        fromTextBox = Integer.parseInt(noOfChannels.getText().toString());
        Log.i(TAG, "ActiveChannels: " + activeChannels);
        if (fromTextBox < 1 || fromTextBox > 7) {
            MsgUtils.showToast("Select between 1-7 channels.", this);
        } else {
            activeChannels = fromTextBox;
            final Intent intent = new Intent(this, BleService.class);
            intent.putExtra("ActiveChannels", activeChannels);
            this.startService(intent);
            for (int i = activeChannels; i < 7; i++) {
                wePotentials[i].setText(R.string.not_active);
            }
        }

    }
}
