package com.example.chemicalsensingapplication.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

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
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Objects;

import static android.os.Environment.DIRECTORY_DOCUMENTS;
import static com.example.chemicalsensingapplication.services.GattActions.ACTION_GATT_CHEMICAL_SENSING_EVENTS;
import static com.example.chemicalsensingapplication.services.GattActions.EVENT;
import static com.example.chemicalsensingapplication.services.GattActions.MULTICHANNEL_DATA;

public class MultiChannelReadActivity extends AppCompatActivity {
    private static final String TAG = MultiChannelReadActivity.class.getSimpleName();

    private BluetoothDevice mSelectedDevice = null;
    private String mDeviceAddress;
    private BleService mBluetoothLeService;
    private TextView mDeviceView;
    private TextView mStatusView;
    private Button mSaveDataButton;
    private TextView[] wePotentials = new TextView[7];
    private TextView noOfChannels;
    private static int activeChannels = 1;
    private static int entryCount = 0;

    private static final DateFormat df = new SimpleDateFormat("yyMMdd_HH:mm"); // Custom date format for file saving

    private final ArrayList<Double> mSampledValues = new ArrayList<>();

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
            }.start();

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
        noOfChannels = findViewById(R.id.edit_number_of_channels);
        mDeviceView = findViewById(R.id.device_view);
        mStatusView = findViewById(R.id.status_view);
        mSaveDataButton = findViewById(R.id.multichannel_save_data);

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

                            mSampledValues.add((double) (timeSinceSamplingStart / 1000));
                            for (int i = 0; i < activeChannels; i++) {
                                mSampledValues.add(potentials[i]);
                            }

                            for (int i = activeChannels; i < 7; i++) {
                                mSampledValues.add(null);
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

    // Request code for creating a csv file.
    private static final int CREATE_FILE = 1;

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createFile() {
        final File dir;
        if (Build.VERSION_CODES.Q > Build.VERSION.SDK_INT) {
            dir = new File(Environment.getExternalStorageDirectory().getPath()
                    + "/Chemical_sensing_data");
        } else {
            dir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).getPath()
                    + "/Chemical_sensing_data");
        }

        String fileName = "Multichannel_" + df.format(Calendar.getInstance().getTime());

        if (!dir.exists())
            dir.mkdir();

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        startActivityForResult(intent, CREATE_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == 1
                && resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that
            // the user selected.
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                try {
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);

                    // Write our sampled values to the created file
                    for (int i = 0; i < mSampledValues.size(); i += 8) {
                        try {
                            if (mSampledValues.get(i) != null) {
                                outputStream.write((mSampledValues.get(i) + ",").getBytes());
                            } else {
                                outputStream.write(("-,").getBytes());
                            }
                            if (mSampledValues.get(i + 1) != null) {
                                outputStream.write((mSampledValues.get(i + 1) + ",").getBytes());
                            } else {
                                outputStream.write(("-,").getBytes());
                            }
                            if (mSampledValues.get(i + 2) != null) {
                                outputStream.write((mSampledValues.get(i + 2) + ",").getBytes());
                            } else {
                                outputStream.write(("-,").getBytes());
                            }
                            if (mSampledValues.get(i + 3) != null) {
                                outputStream.write((mSampledValues.get(i + 3) + ",").getBytes());
                            } else {
                                outputStream.write(("-,").getBytes());
                            }
                            if (mSampledValues.get(i + 4) != null) {
                                outputStream.write((mSampledValues.get(i + 4) + ",").getBytes());
                            } else {
                                outputStream.write(("-,").getBytes());
                            }
                            if (mSampledValues.get(i + 5) != null) {
                                outputStream.write((mSampledValues.get(i + 5) + ",").getBytes());
                            } else {
                                outputStream.write(("-,").getBytes());
                            }
                            if (mSampledValues.get(i + 6) != null) {
                                outputStream.write((mSampledValues.get(i + 6) + ",").getBytes());
                            } else {
                                outputStream.write(("-,").getBytes());
                            }
                            if (mSampledValues.get(i + 7) != null) {
                                outputStream.write((mSampledValues.get(i + 7) + "\n").getBytes());
                            } else {
                                outputStream.write(("-\n").getBytes());
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
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

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void saveData(View view) {
        createFile();
    }
}
