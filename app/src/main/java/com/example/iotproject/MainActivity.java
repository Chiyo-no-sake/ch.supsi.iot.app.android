package com.example.iotproject;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;

import java.util.Collections;
import java.util.List;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {
    private static final int ENABLE_BLUETOOTH_REQUEST_CODE = 1;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 2;

    private static final String TEMP_SERVICE_UUID = "F000AAA0-0451-4000-B000-000000000000";
    final String TEMP_CHAR_UUID = "F000AAA1-0451-4000-B000-000000000000";
    final String DELAY_CHAR_UUID = "F000AAA2-0451-4000-B000-000000000000";

    private static final String LED_SERVICE_UUID = "F0001110-0451-4000-B000-000000000000";
    final String LED1_CHAR_UUID = "F0001111-0451-4000-B000-000000000000";

    private BluetoothAdapter bleAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt connectedGatt;

    private TextView statusValue;
    private Button connectButton;
    private Switch aSwitch;
    private Button aButton;
    private EditText aEdit;

    private boolean connected = false;
    private int delay = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Gather ble components
        bleAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        bleScanner = bleAdapter.getBluetoothLeScanner();
        //Init location permission var


        statusValue = (TextView) findViewById(R.id.statusValue);
        connectButton = (Button) findViewById(R.id.connectButton);
        aButton = (Button) findViewById(R.id.deltaButton);
        aSwitch = (Switch) findViewById(R.id.ledSwitch);
        aEdit = (EditText) findViewById(R.id.inputDelta);

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBleScan();
            }
        });

        // Switch to update the led status
        aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                writeLedStatus(isChecked? 1 : 0);
                System.out.println("Led is now: " + (isChecked ? "on" : "off"));
            }
        });

        // Input text to send new delay to the app
        aButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String value = aEdit.getText().toString();
                if (value.length() == 0) {
                    //Show error
                    Toast.makeText(getApplicationContext(),
                            "You need to set the delta first",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                int newDelay = Integer.parseInt(value);

                writeNewDelay(newDelay);
                System.out.println("New delay: " + value);
            }
        });

        updateButtonStatus();

        // Periodical reading of the temperature status based on delay
        final Handler peridicalReadingHandler = new Handler();
        peridicalReadingHandler.post(new Runnable() {
            @Override
            public void run() {
                readTemperatureLevel();
                peridicalReadingHandler.postDelayed(this, delay);
            }
        });


        ChartController.getInstance().setLineChart((LineChart) findViewById(R.id.lineChart));

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!bleAdapter.isEnabled()) {
            promptBluetoothEnable();
        }

        updateButtonStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ENABLE_BLUETOOTH_REQUEST_CODE) {
            if (resultCode != Activity.RESULT_OK) {
                promptBluetoothEnable();
            }
        }
    }

    private void startBleScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocationPermissionGranted()) {
            requestLocationPermission();
        } else {
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

            final BluetoothDevice[] found = new BluetoothDevice[1];

            final ScanCallback callback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    if (result.getDevice().getName() != null &&
                            result.getDevice().getName().trim().equalsIgnoreCase("iot custom")) {
                        Log.i("ScanCallback", "Found BLE device. Name: " +
                                result.getDevice().getName() + ", address: " + result.getDevice().getAddress());
                        found[0] = result.getDevice();
                    }
                }
            };

            // Callback declaration
            final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    super.onConnectionStateChange(gatt, status, newState);

                    if(status == BluetoothGatt.GATT_SUCCESS){
                        if(newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.w("BluetoothGattCallback", "Successfully connected to " + gatt.getDevice().getName());
                            connectedGatt = gatt;
                            connected = true;
                        }else{
                            Log.w("BluetoothGattCallback", "Disconnected by " + gatt.getDevice().getName());
                            connected = false;
                        }

                        updateButtonStatus();
                    }else{
                        Log.w("BluetoothGattCallback", "Error Connecting");
                    }
                }

                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    super.onCharacteristicRead(gatt, characteristic, status);
                    if(characteristic.getUuid().equals(UUID.fromString(TEMP_CHAR_UUID))){
                        String val = characteristic.getStringValue(0);

                        Log.w("Read From BT Temp", val);
                        ChartController.getInstance().addEntry(Float.parseFloat(val));

                    }else if(characteristic.getUuid().equals(UUID.fromString(DELAY_CHAR_UUID))){
                        int val = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT32, 0);
                        setLocalReadingDelay(val);
                    }
                }

                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    super.onCharacteristicWrite(gatt, characteristic, status);
                    if(characteristic.getUuid().equals(UUID.fromString(DELAY_CHAR_UUID))){
                        Log.w("Writing new delay: ", String.valueOf(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT32, 0)));
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    super.onServicesDiscovered(gatt, status);

                    readSavedDelay();
                }
            };

            //Start scanning
            bleScanner.startScan(null, settings, callback);

            //Stop after 1 second
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    bleScanner.stopScan(callback);

                    if(found[0] != null){
                        found[0].connectGatt(getActivity(), false, gattCallback);
                    }
                }
            }, 1000);


        }
    }

    private void onConnection(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                aButton.setEnabled(true);
                aSwitch.setEnabled(true);
                aEdit.setEnabled(true);
                connectButton.setEnabled(false);

                statusValue.setText("CONNECTED");

                connectedGatt.discoverServices();
            }
        });

    }

    private void onDisconnection(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                aButton.setEnabled(false);
                aSwitch.setEnabled(false);
                aEdit.setEnabled(false);
                connectButton.setEnabled(true);

                statusValue.setText("DISCONNECTED");
            }
        });
    }

    private void updateButtonStatus(){
        if(connected){
            onConnection();
        }else{
            onDisconnection();
        }
    }


    private void requestLocationPermission() {
        if (isLocationPermissionGranted()) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getActivity());
                alertBuilder.setTitle("Location Permission Required")
                        .setMessage("Starting from Android M (6.0), the system requires apps " +
                                "to be granted location access in order to scan for BLE devices.")
                        .setCancelable(false)
                        .setPositiveButton("Grant", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                requestPermission(Manifest.permission.ACCESS_FINE_LOCATION,
                                        LOCATION_PERMISSION_REQUEST_CODE);
                            }
                        });
                alertBuilder.create().show();
            }
        });
    }

    private void setLocalReadingDelay(int ms){
        delay = ms;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                aEdit.setText(String.valueOf(delay));
            }
        });
    }

    // Called by handler each 'delay' seconds
    private void readTemperatureLevel(){
        if(connectedGatt == null) return;
        BluetoothGattService tempService = connectedGatt.getService(UUID.fromString(TEMP_SERVICE_UUID));

        if(tempService == null) return;
        BluetoothGattCharacteristic tempChar = tempService.getCharacteristic(UUID.fromString(TEMP_CHAR_UUID));
        connectedGatt.readCharacteristic(tempChar);
        //after the read, the gatt read callback is called
    }

    private void readSavedDelay(){
        if(connectedGatt == null) return;
        BluetoothGattService tempService = connectedGatt.getService(UUID.fromString(TEMP_SERVICE_UUID));

        if(tempService == null) return;
        BluetoothGattCharacteristic delayChar = tempService.getCharacteristic(UUID.fromString(DELAY_CHAR_UUID));
        connectedGatt.readCharacteristic(delayChar);
        //after the read, the gatt read callback is called
    }

    private void writeNewDelay(int ms){
        if(connectedGatt == null) return;
        BluetoothGattService tempService = connectedGatt.getService(UUID.fromString(TEMP_SERVICE_UUID));

        if(tempService == null) return;
        BluetoothGattCharacteristic delayChar = tempService.getCharacteristic(UUID.fromString(DELAY_CHAR_UUID));
        delayChar.setValue(ms, BluetoothGattCharacteristic.FORMAT_SINT32, 0);
        connectedGatt.writeCharacteristic(delayChar);
        setLocalReadingDelay(ms);
    }

    // Called by the switch when value changes
    private void writeLedStatus(int value){
        if(connectedGatt == null) return;
        BluetoothGattService ledService = connectedGatt.getService(UUID.fromString(LED_SERVICE_UUID));

        if(ledService == null) return;
        BluetoothGattCharacteristic ledChar = ledService.getCharacteristic(UUID.fromString(LED1_CHAR_UUID));
        ledChar.setValue(value, BluetoothGattCharacteristic.FORMAT_SINT8, 0);
        connectedGatt.writeCharacteristic(ledChar);
    }

    private void requestPermission(String permission, int requestCode) {
        String[] permissions = {permission};
        ActivityCompat.requestPermissions(this, permissions, requestCode);
    }

    public Context getActivity() {
        return this;
    }

    public boolean isLocationPermissionGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    void promptBluetoothEnable() {
        // request ble enabling
        Intent enableBle = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBle, ENABLE_BLUETOOTH_REQUEST_CODE);
    }
}