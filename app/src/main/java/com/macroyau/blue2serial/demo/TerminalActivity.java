/**
 * Sharon Fraiman
 * Based on :
 * - BLuetooth to serial library by Macro Yau :  https://github.com/MacroYau/Blue2Serial
 * - GPS location code : https://stackoverflow.com/questions/42218419/how-do-i-implement-the-locationlistener
 */

package com.macroyau.blue2serial.demo;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.content.PermissionChecker;
import android.Manifest;
import android.view.Menu;

import android.os.Handler;

import android.view.MenuItem;
import android.view.View;
import android.view.MotionEvent;

import android.view.WindowManager;
import android.widget.Button;

import android.util.Log;

// GPS Location example taken from :
// https://stackoverflow.com/questions/37373817/android-get-gps-coordinates-and-location
import android.location.LocationManager;
import android.location.LocationListener;

import android.provider.Settings;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.macroyau.blue2serial.BluetoothDeviceListDialog;
import com.macroyau.blue2serial.BluetoothSerial;
import com.macroyau.blue2serial.BluetoothSerialListener;
import java.lang.String;

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
    private static final int ELM327_PERIOD_MILLISECOND = 200; //200;
    private static final int ELM327_STATE_INIT              = 0;
    private static final int ELM327_STATE_SEND_CAF0         = 1;

    private static final int ELM327_STATE_SEND_SH_CAN_MSG_ID = 2; //ELM327_STATE_SEND_SH0B4
    private static final int ELM327_STATE_WAITING_BUTTONS   = 3;
    private static final int ELM327_STATE_PARK_PRESSED   = 4;
    private static final int ELM327_STATE_PARK_RELEASED  = 5;
    private static final int ELM327_STATE_RESET_PRESSED  = 6;
    private static final int ELM327_STATE_RESET_RELEASED = 7;
    private static final int ELM327_STATE_BACKWARD_PRESSED   = 8;
    private static final int ELM327_STATE_BACKWARD_RELEASED  = 9;

    // Initialize to current state as SEND_CAF0, so this will be the first command to be sent
	// Set previous to init, so that oggle will occur, and [SH + CAN MSG ID] (previously was SH0B4) will be sent next,
	// and again back to first stage, until first button is pressed
    private int prev_elm327_state = ELM327_STATE_INIT;
    private int curr_elm327_state = ELM327_STATE_SEND_CAF0;
    // TODO Move to enum

    private int CePKAR_e_VKM_CmdNoAction = 0;
    private int CePKAR_e_VKM_CmdPauseParking = 2;
    private int CePKAR_e_VKM_CmdContinueParking = 3;
    private int CePKAR_e_VKM_CmdResumeParking = 4;


    private Handler handler;
    private Runnable runnableCode;

    private BluetoothSerial bluetoothSerial;

    private String mLastConnectedDeviceName     = "None";
    private String mLastConnectedDeviceAddress  = "None";
    private int mLastMsgSent = 0;
    /* In ARXML 23.23.156.4.2.2 CAN2 SrlDat92_Prtctd_MSG= = 0x255.
 For demo we will use CAN 1 MSG Id = 0x292
  */
    private String CAN_MSG_ID = "255"; //HIL - 255 CT5 - "292"; // "0B4";
    private MenuItem actionConnect, actionDisconnect;

    private boolean crlf = true;

    // My Buttons
    private Button parkButton;
    private Button resetButton;
    private Button backwardButton;
    private Switch grantedSwitch;
    private Boolean mIsGranted = false;
    private int mARC = 0;
    private int mLastParkCmd = -1;  // -1 for none
    private int CAN_MSG_LEN = 8;
    private String[] mCanMsgToSend = new String[CAN_MSG_LEN];

    // GPS Objects
    private LocationManager locationManager;
//    private double lat;
//    private double lng;
    Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        // Keep the window always on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mContext = this;

        // Find UI views and set listeners
        parkButton = (Button) findViewById(R.id.park_button);
        resetButton = (Button) findViewById(R.id.reset_button);
        backwardButton = (Button) findViewById(R.id.backward_button);
        grantedSwitch = (Switch) findViewById(R.id.granted_switch);

        for (int i = 0; i < 6 ; i++){
            mCanMsgToSend[i] = "00";
        }
        mCanMsgToSend[6] = "01";
        // Create a new instance of BluetoothSerial
        bluetoothSerial = new BluetoothSerial(this, this);

        // Create the Handler object (on the main thread by default)
        handler = new Handler();

        // Define the code block to be executed
        runnableCode = new Runnable() {
            @Override
            public void run() {
                int tmp = 0;
                String canMsg = "";
                // Do something here on the main thread
                // Repeat this the same runnable code block again another 200 milliseconds
                // 'this' is referencing the Runnable object
                handler.postDelayed(this, ELM327_PERIOD_MILLISECOND);

                // Reconnect mechanism
                if ((bluetoothSerial.getState() == BluetoothSerial.STATE_DISCONNECTED) &&
                        (mLastConnectedDeviceAddress != "None"))
                {
                    Log.e(myTAG, "Got disconnected. Trying to reconnect ");
                    bluetoothSerial.connect(mLastConnectedDeviceAddress);
                }

                // Send repeat message if no change in state
                if (prev_elm327_state == curr_elm327_state) {
                    // Send last message but increment ACR"
                    // Convert Number to string bit.



                    if (mLastParkCmd == -1){
                        bluetoothSerial.write("\n\r", crlf);   // Repeat the last message transmitted
                    }else {
                        canMsg = buildCanMsg(mLastParkCmd);
                        //bluetoothSerial.write("00 00 00 00 00 00 00", crlf);
                        bluetoothSerial.write(canMsg, crlf);
                    }

                }
                // Initial Step : Send toggle the CAF0 and SH0B4 commands until button is pressed.
                // Control in open loop - assume that sometime the elm327 will accept the two commands
                else {
                    if (curr_elm327_state == ELM327_STATE_SEND_CAF0 ) {
                        bluetoothSerial.write("AT CAF0", crlf);
                        mLastParkCmd = -1;
                        prev_elm327_state = ELM327_STATE_INIT;
                        curr_elm327_state = ELM327_STATE_SEND_SH_CAN_MSG_ID;
                        Log.e(myTAG, "Periodic : Sent CAF0");
                    } else if (curr_elm327_state == ELM327_STATE_SEND_SH_CAN_MSG_ID) {
                        bluetoothSerial.write("ATSH" + CAN_MSG_ID, crlf);
                        mLastParkCmd = -1;
                        prev_elm327_state = ELM327_STATE_INIT;
                        curr_elm327_state = ELM327_STATE_SEND_CAF0;
                        Log.e(myTAG, "Periodic : Sent ATSH" + CAN_MSG_ID);
                    }
                    // Next Step : Two command button logic
                    else {
                        switch(curr_elm327_state){
                            case ELM327_STATE_PARK_PRESSED:
                                mLastParkCmd = CePKAR_e_VKM_CmdContinueParking;
                                canMsg = buildCanMsg(mLastParkCmd);
                                bluetoothSerial.write(canMsg, crlf);
                                Log.e(myTAG, "Periodic : Park Pressed");
                                break;
                            case ELM327_STATE_RESET_PRESSED:
                                mLastParkCmd = CePKAR_e_VKM_CmdNoAction;
                                canMsg = buildCanMsg(mLastParkCmd);
                                bluetoothSerial.write(canMsg, crlf);
                                Log.e(myTAG, "Periodic : Reset Pressed");
                                break;
                            case ELM327_STATE_RESET_RELEASED:
                                mLastParkCmd = CePKAR_e_VKM_CmdNoAction;
                                canMsg = buildCanMsg(mLastParkCmd);
                                bluetoothSerial.write(canMsg, crlf);
                                Log.e(myTAG, "Periodic : Reset Released");
                                break;
                            case ELM327_STATE_PARK_RELEASED:
                                mLastParkCmd = CePKAR_e_VKM_CmdPauseParking;
                                canMsg = buildCanMsg(mLastParkCmd);
                                bluetoothSerial.write(canMsg, crlf);
                                Log.e(myTAG, "Periodic : Park Released");
                                break;
                            case ELM327_STATE_BACKWARD_PRESSED:
                                /* For the demo Nov 2020 - Resume Parking will be trearted as Park Out in EOCM3 SWC_DVIR_LoVelRmtCntrl */
                                mLastParkCmd = CePKAR_e_VKM_CmdResumeParking;
                                canMsg = buildCanMsg(mLastParkCmd);
                                bluetoothSerial.write(canMsg, crlf);
                                Log.e(myTAG, "Periodic : Backward Pressed");
                                break;
                            case ELM327_STATE_BACKWARD_RELEASED:
                                mLastParkCmd = CePKAR_e_VKM_CmdPauseParking;
                                canMsg = buildCanMsg(mLastParkCmd);
                                bluetoothSerial.write(canMsg, crlf);
                                Log.e(myTAG, "Periodic : Backward Released");
                                break;
                            default:
                                break;
                        }

                        prev_elm327_state = curr_elm327_state;  // Only match prev to curr when the initial step is over
                    }
                } // if elm327_State has changed
            }
        };
        // Start the initial runnable task by posting through the handler
        handler.post(runnableCode);

        parkButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        parkButton.setBackgroundColor(Color.CYAN);
                        Log.e(myTAG, "setOnTouchListener : Forward pressed");
                        curr_elm327_state = ELM327_STATE_PARK_PRESSED;
                        return true;
                    case MotionEvent.ACTION_UP:
                        parkButton.setBackgroundColor(Color.GRAY);
                        Log.e(myTAG, "setOnTouchListener : Forward released");
                        curr_elm327_state = ELM327_STATE_PARK_RELEASED;
                        return true;
                }
                return false;
            }
        });  // forwardButton

        resetButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        resetButton.setBackgroundColor(Color.CYAN);
                        Log.e(myTAG, "setOnTouchListener: Reset pressed");
                        curr_elm327_state = ELM327_STATE_RESET_PRESSED;
                        /* Reset granted switch to false */
                        grantedSwitch.setChecked(false);
                        return true;
                    case MotionEvent.ACTION_UP:
                        resetButton.setBackgroundColor(Color.GRAY);
                        Log.e(myTAG, "setOnTouchListener : Reset released");
                        curr_elm327_state = ELM327_STATE_RESET_RELEASED;
                        return true;
                }
                return false;
            }
        }); // resetButton

        backwardButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        backwardButton.setBackgroundColor(Color.CYAN);
                        Log.e(myTAG, "setOnTouchListener: Backword pressed");
                        curr_elm327_state = ELM327_STATE_BACKWARD_PRESSED;
                        return true;
                    case MotionEvent.ACTION_UP:
                        backwardButton.setBackgroundColor(Color.GRAY);
                        Log.e(myTAG, "setOnTouchListener : Backword released");
                        curr_elm327_state = ELM327_STATE_BACKWARD_RELEASED;
                        return true;
                }
                return false;
            }
        }); // backwardButton

        grantedSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked){
                    Log.e(myTAG, "setOnsetOnCheckedChangeListener: Granted Switched");
                    mIsGranted = true;
                }else{
                    Log.e(myTAG, "setOnsetOnCheckedChangeListener: Not Granted Switched");
                    mIsGranted = false;
                }
            }
        }); //grantedSwitch

        // GPS Handling initialization
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (PermissionChecker.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PermissionChecker.PERMISSION_GRANTED)
        {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListenerGPS);
            isLocationEnabled();
            Log.e(myTAG, "GPS connected succefully");
//            Toast.makeText(mContext, "GPS connected succefully",Toast.LENGTH_LONG).show();
        }
        else
        {
            Log.e(myTAG, "GPS permissions Error");
//            Toast.makeText(mContext, "GPS permissions Error",Toast.LENGTH_LONG).show();
            return;
        }

    } // onCreate

    LocationListener locationListenerGPS=new LocationListener() {
        @Override
        public void onLocationChanged(android.location.Location location) {
            double latitude=location.getLatitude();
            double longitude=location.getLongitude();
            String msg="GPS : Latitude: " + latitude + "Longitude: "+longitude;
//            Toast.makeText(mContext, msg,Toast.LENGTH_LONG).show();
            Log.e(myTAG, msg);
        }
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };

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

        isLocationEnabled();
    }

    private void isLocationEnabled() {

        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            AlertDialog.Builder alertDialog=new AlertDialog.Builder(mContext);
            alertDialog.setTitle("Enable Location");
            alertDialog.setMessage("Your locations setting is not enabled. Please enabled it in settings menu.");
            alertDialog.setPositiveButton("Location Settings", new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface dialog, int which){
                    Intent intent=new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                }
            });
            alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface dialog, int which){
                    dialog.cancel();
                }
            });
            AlertDialog alert=alertDialog.create();
            alert.show();
        }
        else{
//            AlertDialog.Builder alertDialog=new AlertDialog.Builder(mContext);
//            alertDialog.setTitle("Confirm Location");
//            alertDialog.setMessage("Your Location is enabled, please enjoy");
//            alertDialog.setNegativeButton("Back to interface",new DialogInterface.OnClickListener(){
//                public void onClick(DialogInterface dialog, int which){
//                    dialog.cancel();
//                }
//            });
//            AlertDialog alert=alertDialog.create();
//            alert.show();
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
        } /* else if (id == R.id.action_crlf) {
            crlf = !item.isChecked();
            item.setChecked(crlf);
            return true;
        }*/

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



     public String buildCanMsg(int PrkCmdString) {
        String msg = "";
        int tmp = 0;

        int arc = getARC();
        arc = arc << 2;
        mCanMsgToSend[7] =  String.format("2%1$s",Integer.toString(arc, 16));

        if (mIsGranted){
            // turn on SuprRmtPrkVirtKeyEntlmtStsAuth bit
            tmp = tmp | 8; //8dec = 1000;
        }

        tmp = tmp | PrkCmdString;

        mCanMsgToSend[4] = String.format("%1$s0", Integer.toString(tmp, 16) ); // Convert tmp from int to hex format

        msg = mCanMsgToSend[0];
        for (int i = 1; i < 8 ; i++){
            msg = String.format("%1$s %2$s", msg, mCanMsgToSend[i]);
        }

        return msg;

        // Bytes Layouts:
        // Byte #7 and #4 are changing, all the rest are constant.
        // Byte #7 = VirtKeyMblDevLocDistAuth[1]  VirtKeyMblDevLocZnAuth[3]=2-OutsideVehWLZ(WithinLocation zone)  SD92P_ARC[2] 00
        // 0 010 00 00

        //Byte #6 = VirtKeyMblDevLocDirAuth[1] VirtKeyMblDevLocDistAuth[6]=2
        //VirtKeyMblDevLocDistAuth - Always send const value=2 (No need for a radio button)
        // 0000 0001

        //Byte #5 = VirtKeyMblDevLocDirAuth[7]
        // 0000 0000

        // Byte #4 =SuprRmtPrkVirtKeyEntlmtStsAuth[1]=Not Granted/Granted SuprRmtPrkVirtKeyPrkCmdAuth[3] VirtKeyMblDevDgtlKeyIdxAuth[4]
        // 0 000 0000
        //SuprRmtPrkVirtKeyEntlmtStsAuth:
        //CePKAR_e_VKM_StatEntlNotGranted     (0) // Not Granted
        //CePKAR_e_VKM_StatEntlGranted        (1) // Granted
        //SuprRmtPrkVirtKeyPrkCmdAuth:
         //CePKAR_e_VKM_CmdNoAction            (0) //Always send PrkCmd – NoAction
         //CePKAR_e_VKM_CmdInitiateParking        (1)
         //CePKAR_e_VKM_CmdPauseParking      (2)  //When releasing button – PauseParking
         //CePKAR_e_VKM_CmdContinueParking       (3) //When pressing on a button – Continue Parking
         //CePKAR_e_VKM_CmdResumeParking          (4)

    }

    // Get Automatic Rolling Counter - 0, 1, 2,3
    private int getARC() {
        // Keep current counter
        int ret = mARC;
        // Increment the rolling counter
        if (mARC == 3){
            mARC = 0;
        }else{
            mARC++;
        }

        return ret;
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
                parkButton.setBackgroundColor(Color.GRAY);
                resetButton.setBackgroundColor(Color.GRAY);
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
        parkButton.setBackgroundColor(Color.GRAY);
        resetButton.setBackgroundColor(Color.GRAY);
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
        parkButton.setBackgroundColor(Color.GRAY);
        resetButton.setBackgroundColor(Color.GRAY);
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
		// Save the name and address of bluetooth device for automatic reconnect
        mLastConnectedDeviceName = bluetoothSerial.getConnectedDeviceName();
        mLastConnectedDeviceAddress = bluetoothSerial.getConnectedDeviceAddress();
        Log.e(myTAG, "Sharon : onBluetoothDeviceConnected");
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
