package com.example.smartsteppedometer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.location.Location;
import android.os.Bundle;
import android.widget.TextView;
import android.view.View;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends FragmentActivity implements SensorEventListener, OnMapReadyCallback {

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


    // Map & GPS
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private List<LatLng> pathPoints = new ArrayList<>();
    private Polyline polyline;


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

        //Map & GPS
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        mapFragment.getMapAsync(this);


        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isTracking = true;
                sensorManager.registerListener(MainActivity.this, accelerometer, SensorManager.SENSOR_DELAY_UI);
                sensorManager.registerListener(MainActivity.this, gyroscope, SensorManager.SENSOR_DELAY_UI);
                startLocationUpdates();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isTracking = false;
                sensorManager.unregisterListener(MainActivity.this);
                fusedLocationClient.removeLocationUpdates(locationCallback);
            }
        });

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepCount = 0;
                stepCounterText.setText("Steps: " + stepCount);
                pathPoints.clear();
                if (polyline != null) polyline.remove();
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


    // --- GPS & Map ---
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
    }

    private void startLocationUpdates() {
        LocationRequest request = LocationRequest.create();
        request.setInterval(3000);
        request.setFastestInterval(2000);
        request.setPriority(Priority.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                for (Location location : result.getLocations()) {
                    LatLng point = new LatLng(location.getLatitude(), location.getLongitude());
                    pathPoints.add(point);
                    updatePath();
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        fusedLocationClient.requestLocationUpdates(request, locationCallback, null);
    }

    private void updatePath() {
        if (mMap == null || pathPoints.isEmpty()) return;
        if (polyline != null) polyline.remove();
        polyline = mMap.addPolyline(new PolylineOptions().addAll(pathPoints).color(0xFF3F51B5).width(10f));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pathPoints.get(pathPoints.size() - 1), 17f));
    }
}
