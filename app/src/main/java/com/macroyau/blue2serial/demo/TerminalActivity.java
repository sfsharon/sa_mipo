package com.macroyau.blue2serial.demo;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.Menu;

import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;
import android.view.MotionEvent;

import android.widget.Button;

import android.util.Log;

import com.macroyau.blue2serial.BluetoothDeviceListDialog;
import com.macroyau.blue2serial.BluetoothSerial;
import com.macroyau.blue2serial.BluetoothSerialListener;

/**
 * This is an example Bluetooth terminal application built using the Blue2Serial library.
 *
 * @author Macro Yau
 */
public class TerminalActivity extends AppCompatActivity
        implements BluetoothSerialListener, BluetoothDeviceListDialog.OnDeviceSelectedListener {

    private static final int REQUEST_ENABLE_BLUETOOTH = 1;

    private static final String myTAG = "ELM327Activity";
    private boolean hasReceivedCAF0  = false;
    private boolean hasReceivedSH0B4 = false;

    private BluetoothSerial bluetoothSerial;

    private MenuItem actionConnect, actionDisconnect;

    private boolean crlf = true;

    private Button forwardButton;
    private Button backwardButton;
    private Button initButton;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        // Find UI views and set listeners
        forwardButton  = (Button) findViewById(R.id.forward_button);
        backwardButton = (Button) findViewById(R.id.backward_button);
        initButton     = (Button) findViewById(R.id.init_elm327);

        // Create a new instance of BluetoothSerial
        bluetoothSerial = new BluetoothSerial(this, this);


        forwardButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        bluetoothSerial.write("00 01 00 00 00 00 00", crlf);
                        forwardButton.setBackgroundColor(Color.CYAN);
                        Log.e(myTAG, "Forward pressed");
                        return true;
                    case MotionEvent.ACTION_UP:
                        bluetoothSerial.write("00 00 00 00 00 00 00", crlf);
                        forwardButton.setBackgroundColor(Color.RED);
                        Log.e(myTAG, "Forward released");
                        return true;
                }
                return false;
            }
        });

        backwardButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        bluetoothSerial.write("00 00 01 00 00 00 00", crlf);
                        backwardButton.setBackgroundColor(Color.CYAN);
                        Log.e(myTAG, "Backword pressed");
                        return true;
                    case MotionEvent.ACTION_UP:
                        bluetoothSerial.write("00 00 00 00 00 00 00", crlf);
                        backwardButton.setBackgroundColor(Color.RED);
                        Log.e(myTAG, "Backword released");
                        return true;
                }
                return false;
            }
        });

        initButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        bluetoothSerial.write("AT CAF0", crlf);
                        initButton.setBackgroundColor(Color.GRAY);
                        Log.e(myTAG, "Init pressed");
                        return true;
                    case MotionEvent.ACTION_UP:
                        bluetoothSerial.write("ATSH0B4", crlf);
                        Log.e(myTAG, "Init released");
                        return true;
                }
                return false;
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();

        // Check Bluetooth availability on the device and set up the Bluetooth adapter
        bluetoothSerial.setup();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Open a Bluetooth serial port and get ready to establish a connection
        if (bluetoothSerial.checkBluetooth() && bluetoothSerial.isBluetoothEnabled()) {
            if (!bluetoothSerial.isConnected()) {
                bluetoothSerial.start();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Disconnect from the remote device and close the serial port
        bluetoothSerial.stop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_terminal, menu);

        actionConnect = menu.findItem(R.id.action_connect);
        actionDisconnect = menu.findItem(R.id.action_disconnect);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_connect) {
            showDeviceListDialog();
            return true;
        } else if (id == R.id.action_disconnect) {
            bluetoothSerial.stop();
            return true;
        } else if (id == R.id.action_crlf) {
            crlf = !item.isChecked();
            item.setChecked(crlf);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void invalidateOptionsMenu() {
        if (bluetoothSerial == null)
            return;

        // Show or hide the "Connect" and "Disconnect" buttons on the app bar
        if (bluetoothSerial.isConnected()) {
            if (actionConnect != null)
                actionConnect.setVisible(false);
            if (actionDisconnect != null)
                actionDisconnect.setVisible(true);
        } else {
            if (actionConnect != null)
                actionConnect.setVisible(true);
            if (actionDisconnect != null)
                actionDisconnect.setVisible(false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_ENABLE_BLUETOOTH:
                // Set up Bluetooth serial port when Bluetooth adapter is turned on
                if (resultCode == Activity.RESULT_OK) {
                    bluetoothSerial.setup();
                }
                break;
        }
    }

    private void updateBluetoothState() {
        // Get the current Bluetooth state
        final int state;
        if (bluetoothSerial != null)
            state = bluetoothSerial.getState();
        else
            state = BluetoothSerial.STATE_DISCONNECTED;

        // Display the current state on the app bar as the subtitle
        String subtitle;
        switch (state) {
            case BluetoothSerial.STATE_CONNECTING:
                subtitle = getString(R.string.status_connecting);
                break;
            case BluetoothSerial.STATE_CONNECTED:
                subtitle = getString(R.string.status_connected, bluetoothSerial.getConnectedDeviceName());
                break;
            default:
                subtitle = getString(R.string.status_disconnected);
                initButton.setBackgroundColor(Color.GRAY);
				 hasReceivedCAF0  = false;
                 hasReceivedSH0B4 = false;
                break;
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(subtitle);
        }
    }

    private void showDeviceListDialog() {
        // Display dialog for selecting a remote Bluetooth device
        BluetoothDeviceListDialog dialog = new BluetoothDeviceListDialog(this);
        dialog.setOnDeviceSelectedListener(this);
        dialog.setTitle(R.string.paired_devices);
        dialog.setDevices(bluetoothSerial.getPairedDevices());
        dialog.showAddress(true);
        dialog.show();
    }

    /* Implementation of BluetoothSerialListener */

    @Override
    public void onBluetoothNotSupported() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.no_bluetooth)
                .setPositiveButton(R.string.action_quit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    @Override
    public void onBluetoothDisabled() {
        Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBluetooth, REQUEST_ENABLE_BLUETOOTH);
    }

    @Override
    public void onBluetoothDeviceDisconnected() {
        invalidateOptionsMenu();
        updateBluetoothState();
    }

    @Override
    public void onConnectingBluetoothDevice() {
        updateBluetoothState();
    }

    @Override
    public void onBluetoothDeviceConnected(String name, String address) {
        invalidateOptionsMenu();
        updateBluetoothState();
    }

    @Override
    public void onBluetoothSerialRead(String message) {
        Log.e(myTAG, "RECEIVED :: " + message);
        if (message.startsWith("AT CAF0")) {
            Log.e(myTAG, "Got CAF0 ");
            hasReceivedCAF0  = true;
        } else if (message.startsWith("ATSH0B4")) {
            Log.e(myTAG, "Got SH0B4 ");
            hasReceivedSH0B4 = true;
        }

        if ((hasReceivedCAF0 == true) && (hasReceivedSH0B4 == true)) {
                initButton.setBackgroundColor(Color.GREEN);
                Log.e(myTAG, "Init succeeded");
        }
    }

    @Override
    public void onBluetoothSerialWrite(String message) {
    }

    /* Implementation of BluetoothDeviceListDialog.OnDeviceSelectedListener */

    @Override
    public void onBluetoothDeviceSelected(BluetoothDevice device) {
        // Connect to the selected remote Bluetooth device
        bluetoothSerial.connect(device);
    }

    /* End of the implementation of listeners */

    private final Runnable scrollTerminalToBottom = new Runnable() {
        @Override
        public void run() {
        }
    };

}
