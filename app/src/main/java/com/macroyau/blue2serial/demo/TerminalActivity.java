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
import android.view.Menu;

import android.os.Handler;

import android.view.MenuItem;
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

    // ELM327 data
    private static final String myTAG = "ELM327Activity";
    private static final int ELM327_PERIOD_MILLISECOND = 200;
    private static final int ELM327_STATE_INIT              = 0;
    private static final int ELM327_STATE_SEND_CAF0         = 1;
    private static final int ELM327_STATE_SEND_SH0B4        = 2;
    private static final int ELM327_STATE_WAITING_BUTTONS   = 3;
    private static final int ELM327_STATE_FORWARD_PRESSED   = 4;
    private static final int ELM327_STATE_FORWARD_RELEASED  = 5;
    private static final int ELM327_STATE_BACKWORD_PRESSED  = 6;
    private static final int ELM327_STATE_BACKWORD_RELEASED = 7;

    // Initialize to current state as SEND_CAF0, so this will be the first command to be sent
	// Set previous to init, so that oggle will occur, and SH0B4 will be sent next,
	// and again back to first stage, until first button is pressed
    private int prev_elm327_state = ELM327_STATE_INIT;
    private int curr_elm327_state = ELM327_STATE_SEND_CAF0;


    private Handler handler;
    private Runnable runnableCode;

    private BluetoothSerial bluetoothSerial;

    private MenuItem actionConnect, actionDisconnect;

    private boolean crlf = true;

    // My Buttons
    private Button forwardButton;
    private Button backwardButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        // Find UI views and set listeners
        forwardButton  = (Button) findViewById(R.id.forward_button);
        backwardButton = (Button) findViewById(R.id.backward_button);

        // Create a new instance of BluetoothSerial
        bluetoothSerial = new BluetoothSerial(this, this);

        // Create the Handler object (on the main thread by default)
        handler = new Handler();

        // Define the code block to be executed
        runnableCode = new Runnable() {
            @Override
            public void run() {
                // Do something here on the main thread
                // Repeat this the same runnable code block again another 2 seconds
                // 'this' is referencing the Runnable object
                handler.postDelayed(this, ELM327_PERIOD_MILLISECOND);

                // Send repeat message if no change in state
                if (prev_elm327_state == curr_elm327_state) {
                    bluetoothSerial.write("\n\r", crlf);   // Repeat the last message transmitted
                }
                // Initial Step : Send toggle the CAF0 and SH0B4 commands until button is pressed.
                // Control in open loop - assume that sometime the elm327 will accept the two commands
                else {
                    if (curr_elm327_state == ELM327_STATE_SEND_CAF0 ) {
                        bluetoothSerial.write("AT CAF0", crlf);
                        prev_elm327_state = ELM327_STATE_INIT;
                        curr_elm327_state = ELM327_STATE_SEND_SH0B4;
                        Log.e(myTAG, "Periodic : Sent CAF0");
                    } else if (curr_elm327_state == ELM327_STATE_SEND_SH0B4) {
                        bluetoothSerial.write("ATSH0B4", crlf);
                        prev_elm327_state = ELM327_STATE_INIT;
                        curr_elm327_state = ELM327_STATE_SEND_CAF0;
                        Log.e(myTAG, "Periodic : Sent ATSH0B4");
                    }
                    // Next Step : Two command button logic
                    else {
                        if (curr_elm327_state == ELM327_STATE_FORWARD_PRESSED) {
                            bluetoothSerial.write("00 01 00 00 00 00 00", crlf);
                            Log.e(myTAG, "Periodic : Forward Pressed");
                        } else if (curr_elm327_state == ELM327_STATE_BACKWORD_PRESSED) {
                            bluetoothSerial.write("00 00 01 00 00 00 00", crlf);
                            Log.e(myTAG, "Periodic : Backward Pressed");
                        } else if ((curr_elm327_state == ELM327_STATE_BACKWORD_RELEASED) ||
                                (curr_elm327_state == ELM327_STATE_FORWARD_RELEASED)) {
                            bluetoothSerial.write("00 00 00 00 00 00 00", crlf);
                            Log.e(myTAG, "Periodic : Button Released");
                        }
                        prev_elm327_state = curr_elm327_state;  // Only match prev to curr when the initial step is over
                    }
                } // if elm327_State has changed
            }
        };
        // Start the initial runnable task by posting through the handler
        handler.post(runnableCode);

        forwardButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        forwardButton.setBackgroundColor(Color.CYAN);
                        Log.e(myTAG, "setOnTouchListener : Forward pressed");
                        curr_elm327_state = ELM327_STATE_FORWARD_PRESSED;
                        return true;
                    case MotionEvent.ACTION_UP:
                        forwardButton.setBackgroundColor(Color.RED);
                        Log.e(myTAG, "setOnTouchListener : Forward released");
                        curr_elm327_state = ELM327_STATE_FORWARD_RELEASED;
                        return true;
                }
                return false;
            }
        });  // forwardButton

        backwardButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        backwardButton.setBackgroundColor(Color.CYAN);
                        Log.e(myTAG, "setOnTouchListener: Backword pressed");
                        curr_elm327_state = ELM327_STATE_BACKWORD_PRESSED;
                        return true;
                    case MotionEvent.ACTION_UP:
                        backwardButton.setBackgroundColor(Color.RED);
                        Log.e(myTAG, "setOnTouchListener : Backword released");
                        curr_elm327_state = ELM327_STATE_BACKWORD_RELEASED;
                        return true;
                }
                return false;
            }
        }); // backwardButton
    } // onCreate

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
				// Bring back to init state
                forwardButton.setBackgroundColor(Color.GRAY);
                backwardButton.setBackgroundColor(Color.GRAY);
                prev_elm327_state = ELM327_STATE_INIT;
                curr_elm327_state = ELM327_STATE_SEND_CAF0;
                 Log.e(myTAG, "updateBluetoothState :  disconnected");
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
        // Bring back to init state
        forwardButton.setBackgroundColor(Color.GRAY);
        backwardButton.setBackgroundColor(Color.GRAY);
        prev_elm327_state = ELM327_STATE_INIT;
        curr_elm327_state = ELM327_STATE_SEND_CAF0;
        Log.e(myTAG, "onBluetoothDisabled");
    }

    @Override
    public void onBluetoothDeviceDisconnected() {
        invalidateOptionsMenu();
        updateBluetoothState();
        // Bring back to init state
        forwardButton.setBackgroundColor(Color.GRAY);
        backwardButton.setBackgroundColor(Color.GRAY);
        prev_elm327_state = ELM327_STATE_INIT;
        curr_elm327_state = ELM327_STATE_SEND_CAF0;
        Log.e(myTAG, "onBluetoothDeviceDisconnected");
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
