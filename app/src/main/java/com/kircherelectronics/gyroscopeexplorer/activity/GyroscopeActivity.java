package com.kircherelectronics.gyroscopeexplorer.activity;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.kircherelectronics.fsensor.filter.averaging.MeanFilter;
import com.kircherelectronics.fsensor.filter.gyroscope.OrientationGyroscope;
import com.kircherelectronics.fsensor.filter.gyroscope.fusion.complimentary.OrientationFusedComplimentary;
import com.kircherelectronics.fsensor.filter.gyroscope.fusion.kalman.OrientationFusedKalman;
import com.kircherelectronics.fsensor.util.rotation.RotationUtil;
import com.kircherelectronics.gyroscopeexplorer.R;
import com.kircherelectronics.gyroscopeexplorer.datalogger.DataLoggerManager;
import com.kircherelectronics.gyroscopeexplorer.gauge.GaugeBearing;
import com.kircherelectronics.gyroscopeexplorer.gauge.GaugeRotation;
import com.kircherelectronics.gyroscopeexplorer.view.VectorDrawableButton;

import org.apache.commons.math3.complex.Quaternion;

import java.util.Arrays;
import java.util.Locale;


public class GyroscopeActivity extends AppCompatActivity implements SensorEventListener {
    private static final String tag = GyroscopeActivity.class.getSimpleName();

    private final static int WRITE_EXTERNAL_STORAGE_REQUEST = 1000;

    // Indicate if the output should be logged to a .csv file
    private boolean logData = false;

    private boolean hasAcceleration = false;
    private boolean hasMagnetic = false;

    private boolean meanFilterEnabled;
    private boolean kalmanFilterEnabled;
    private boolean complimentaryFilterEnabled;

    private float[] fusedOrientation = new float[3];
    private float[] acceleration = new float[4];
    private float[] magnetic = new float[3];
    private float[] rotation = new float[3];

    private Mode mode = Mode.GYROSCOPE_ONLY;

    // The gauge views. Note that these are views and UI hogs since they run in
    // the UI thread, not ideal, but easy to use.
    private GaugeBearing gaugeBearingCalibrated;
    private GaugeRotation gaugeTiltCalibrated;

    // Handler for the UI plots so everything plots smoothly
    protected Handler handler;

    protected Runnable runable;

    private TextView tvXAxis;
    private TextView tvYAxis;
    private TextView tvZAxis;

    private OrientationGyroscope orientationGyroscope;
    private OrientationFusedComplimentary orientationComplimentaryFusion;
    private OrientationFusedKalman orientationKalmanFusion;

    private MeanFilter meanFilter;

    private SensorManager sensorManager;

    private DataLoggerManager dataLogger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_gyroscope);
        dataLogger = new DataLoggerManager(this);
        meanFilter = new MeanFilter();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        //initUI();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.gyroscope, menu);
        return true;
    }

    /**
     * Event Handling for Individual menu item selected Identify single menu
     * item by it's id
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            // Reset everything
            case R.id.action_reset:
                if(orientationGyroscope != null) {
                    orientationGyroscope.reset();
                }
                return true;

            // Reset everything
            case R.id.action_config:
                Intent intent = new Intent();
                intent.setClass(this, ConfigActivity.class);
                startActivity(intent);
                return true;

            // Reset everything
            case R.id.action_help:
                showHelpDialog();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        requestPermissions();
        readPrefs();

        switch (mode) {
            case GYROSCOPE_ONLY:
                orientationGyroscope = new OrientationGyroscope();
                break;
            case COMPLIMENTARY_FILTER:
                orientationComplimentaryFusion = new OrientationFusedComplimentary();
                break;
            case KALMAN_FILTER:
                orientationKalmanFusion = new OrientationFusedKalman();
                break;
        }

        reset();

        if(orientationKalmanFusion != null) {
            orientationKalmanFusion.startFusion();
        }

        sensorManager.registerListener(this, sensorManager
                        .getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);

        // Register for sensor updates.
        sensorManager.registerListener(this, sensorManager
                        .getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_FASTEST);

        // Register for sensor updates.
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_FASTEST);

        handler.post(runable);
    }

    @Override
    public void onPause() {
        super.onPause();

        if(orientationKalmanFusion != null) {
            orientationKalmanFusion.stopFusion();
        }

        sensorManager.unregisterListener(this);
        handler.removeCallbacks(runable);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Android reuses events, so you probably want a copy
            System.arraycopy(event.values, 0, acceleration, 0, event.values.length);
            hasAcceleration = true;
        } else  if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            // Android reuses events, so you probably want a copy
            System.arraycopy(event.values, 0, magnetic, 0, event.values.length);
            hasMagnetic = true;
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            // Android reuses events, so you probably want a copy
            System.arraycopy(event.values, 0, rotation, 0, event.values.length);

            switch (mode) {
                case GYROSCOPE_ONLY:
                    if(!orientationGyroscope.isBaseOrientationSet()) {
                        orientationGyroscope.setBaseOrientation(Quaternion.IDENTITY);
                    } else {
                        fusedOrientation = orientationGyroscope.calculateOrientation(rotation, event.timestamp);
                    }

                    break;
                case COMPLIMENTARY_FILTER:
                    if(!orientationComplimentaryFusion.isBaseOrientationSet()) {
                        if(hasAcceleration && hasMagnetic) {
                            orientationComplimentaryFusion.setBaseOrientation(RotationUtil.getOrientationVectorFromAccelerationMagnetic(acceleration, magnetic));
                        }
                    } else {
                        fusedOrientation = orientationComplimentaryFusion.calculateFusedOrientation(rotation, event.timestamp, acceleration, magnetic);
                        Log.d("kbk", "Process Orientation Fusion Complimentary: " + Arrays.toString(fusedOrientation));
                    }

                    break;
                case KALMAN_FILTER:

                    if(!orientationKalmanFusion.isBaseOrientationSet()) {
                        if(hasAcceleration && hasMagnetic) {
                            orientationKalmanFusion.setBaseOrientation(RotationUtil.getOrientationVectorFromAccelerationMagnetic(acceleration, magnetic));
                        }
                    } else {
                        fusedOrientation = orientationKalmanFusion.calculateFusedOrientation(rotation, event.timestamp, acceleration, magnetic);
                    }
                    break;
            }

            if(meanFilterEnabled) {
                fusedOrientation = meanFilter.filter(fusedOrientation);
            }

            dataLogger.setRotation(fusedOrientation);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {}


    private boolean getPrefMeanFilterEnabled() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        return prefs.getBoolean(ConfigActivity.MEAN_FILTER_SMOOTHING_ENABLED_KEY,
                false);
    }

    private float getPrefMeanFilterTimeConstant() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        return Float.valueOf(prefs.getString(ConfigActivity.MEAN_FILTER_SMOOTHING_TIME_CONSTANT_KEY, "0.5"));
    }

    private boolean getPrefKalmanEnabled() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        return prefs.getBoolean(ConfigActivity.KALMAN_QUATERNION_ENABLED_KEY,
                false);
    }

    private boolean getPrefComplimentaryEnabled() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        return prefs.getBoolean(ConfigActivity.COMPLIMENTARY_QUATERNION_ENABLED_KEY,
                false);
    }

    private float getPrefImuOCfQuaternionCoeff() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        return Float.valueOf(prefs.getString(
                ConfigActivity.COMPLIMENTARY_QUATERNION_COEFF_KEY, "0.5"));
    }

    /*private void initStartButton() {
        final VectorDrawableButton button = findViewById(R.id.button_start);

        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!logData) {
                    button.setText("Stop Log");
                    startDataLog();
                } else {
                    button.setText("Start Log");
                    stopDataLog();
                }
            }
        });
    }
*/
    /**
     * Initialize the UI.
     */
   /* private void initUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if(getSupportActionBar()!=null) {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // Initialize the calibrated text views
        tvXAxis = this.findViewById(R.id.value_x_axis_calibrated);
        tvYAxis = this.findViewById(R.id.value_y_axis_calibrated);
        tvZAxis = this.findViewById(R.id.value_z_axis_calibrated);

        // Initialize the calibrated gauges views
        gaugeBearingCalibrated = findViewById(R.id.gauge_bearing_calibrated);
        gaugeTiltCalibrated = findViewById(R.id.gauge_tilt_calibrated);

        initStartButton();
    }*/

    private void reset() {

        switch (mode) {
            case GYROSCOPE_ONLY:
                orientationGyroscope.reset();
                break;
            case COMPLIMENTARY_FILTER:
                orientationComplimentaryFusion.reset();
                break;
            case KALMAN_FILTER:
                orientationKalmanFusion.reset();
                break;
        }

        acceleration = new float[4];
        magnetic = new float[3];

        hasAcceleration = false;
        hasMagnetic = false;

        handler = new Handler();

        runable = new Runnable() {
            @Override
            public void run() {
                handler.postDelayed(this, 100);
                updateText();
                updateGauges();
            }
        };
    }

    private void readPrefs() {
        meanFilterEnabled = getPrefMeanFilterEnabled();
        complimentaryFilterEnabled = getPrefComplimentaryEnabled();
        kalmanFilterEnabled = getPrefKalmanEnabled();

        if(meanFilterEnabled) {
            meanFilter.setTimeConstant(getPrefMeanFilterTimeConstant());
        }

        if(!complimentaryFilterEnabled && !kalmanFilterEnabled) {
            mode = Mode.GYROSCOPE_ONLY;
        } else if(complimentaryFilterEnabled) {
            mode = Mode.COMPLIMENTARY_FILTER;
        } else if(kalmanFilterEnabled) {
            mode = Mode.KALMAN_FILTER;
        }
    }

    private void showHelpDialog() {
        Dialog helpDialog = new Dialog(this);

        helpDialog.setCancelable(true);
        helpDialog.setCanceledOnTouchOutside(true);
        helpDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = getLayoutInflater()
                .inflate(R.layout.layout_help_home, null);

        helpDialog.setContentView(view);

        helpDialog.show();
    }

    private void startDataLog() {
        logData = true;
        dataLogger.startDataLog();
    }

    private void stopDataLog() {
        logData = false;
        String path = dataLogger.stopDataLog();
        Toast.makeText(this, "File Written to: " + path, Toast.LENGTH_SHORT).show();
    }

    private void updateText() {
        tvXAxis.setText(String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(fusedOrientation[0]) + 360) % 360));
        tvYAxis.setText(String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(fusedOrientation[1]) + 360) % 360));
        tvZAxis.setText(String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(fusedOrientation[2]) + 360) % 360));
    }

    private void updateGauges() {
        gaugeBearingCalibrated.updateBearing(fusedOrientation[2]);
        gaugeTiltCalibrated.updateRotation(fusedOrientation);
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_REQUEST);
        }
    }

    private enum Mode {
        GYROSCOPE_ONLY,
        COMPLIMENTARY_FILTER,
        KALMAN_FILTER;
    }

}
