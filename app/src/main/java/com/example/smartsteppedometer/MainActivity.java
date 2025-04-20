package com.example.smartsteppedometer;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;
import android.view.View;
import android.widget.Button;


public class MainActivity extends Activity implements SensorEventListener {

    // All Sensors and their manager and individual sensors
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;

    // Used for gravity filtering in step detection
    private float[] gravity = new float[3];
    // Step counter and UI element
    private int stepCount = 0;
    private TextView stepCounterText;

    // Parameters to control sensitivity and prevent double-counting steps
    private static final float THRESHOLD = 10.0f; // Acceleration magnitude threshold
    private long lastStepTime = 0; // Last time a step was counted
    private static final int STEP_DELAY_NS = 250_000_000; // 250ms between steps seems reasonable
    private boolean isTracking = false; // Tracking won't start until the user presses Start

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        stepCounterText = findViewById(R.id.stepCounterText);

        Button startButton = findViewById(R.id.startButton); // Start button
        Button stopButton = findViewById(R.id.stopButton); // Stop Button
        Button resetButton = findViewById(R.id.resetButton); // Reset button

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        // Default: not listening until start button is pressed
        isTracking = false;

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isTracking = true;
                sensorManager.registerListener(MainActivity.this, accelerometer, SensorManager.SENSOR_DELAY_UI);
                sensorManager.registerListener(MainActivity.this, gyroscope, SensorManager.SENSOR_DELAY_UI);
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isTracking = false;
                sensorManager.unregisterListener(MainActivity.this);
            }
        });

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepCount = 0;
                stepCounterText.setText("Steps: " + stepCount);
            }
        });
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isTracking) return;
        // Listen for accelerometer data only (gyro optional for refinement)
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            detectStep(event);
        }
    }

    // Core step detection logic using linear acceleration
    private void detectStep(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        // Use a high-pass filter to remove gravity from the raw values
        final float alpha = 0.8f;
        gravity[0] = alpha * gravity[0] + (1 - alpha) * x;
        gravity[1] = alpha * gravity[1] + (1 - alpha) * y;
        gravity[2] = alpha * gravity[2] + (1 - alpha) * z;

        // Get linear acceleration values
        float linearX = x - gravity[0];
        float linearY = y - gravity[1];
        float linearZ = z - gravity[2];

        // Calculate the magnitude of motion vector
        float magnitude = (float) Math.sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ);

        // Use time + magnitude threshold to filter out noise and false positives (ANNOYING)
        long now = System.nanoTime();
        if (magnitude > THRESHOLD && (now - lastStepTime) > STEP_DELAY_NS) {
            stepCount++;
            lastStepTime = now;
            stepCounterText.setText("Steps: " + stepCount);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI);
    }
}
