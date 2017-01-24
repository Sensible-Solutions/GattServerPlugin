/*
* Copyright (C) 2015-2017 Sensible Solutions Sweden AB
*
*
* Cordova Plugin for the Bluetooth GATT Profile server role.
*
* This class provides Bluetooth GATT server role functionality,
* allowing applications to create and advertise the Bluetooth
* Smart immediate alert service.
* 
*/
 
package com.sensiblesolutions.gattserver;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattDescriptor;
import android.media.RingtoneManager;
import android.media.Ringtone;
import android.net.Uri;
//import android.support.v4.app.NotificationCompat.Builder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;	// Added 2017-01-18
import android.app.NotificationManager;
//import android.app.ActivityManager;				// Added 2017-01-09
//import android.app.ActivityManager.RunningAppProcessInfo;	// Added 2017-01-09
//import android.app.Notification;
import android.R;
import android.app.AlertDialog;			// For showing debug messaages
import android.content.DialogInterface;		// For showing debug messaages

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class GattServerPlugin extends CordovaPlugin
{
	// Immediate alert service
	private final static UUID IMMEDIATE_ALERT_SERVICE_UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");	// Service UUID
	//private final static UUID ALERT_LEVEL_CHAR_UUID = UUID.fromString("00002A06-0000-1000-8000-00805f9b34fb");		// Characteristic UUID (removed 2017-01-13)
	private final static UUID ALERT_LEVEL_CHAR_UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");		// Characteristic UUID (added 2017-01-13)
	//private static final int ALERT_LEVEL_CHARACTERISTIC_VALUE = 2;
	//private static final int ALERT_LEVEL_CHARACTERISTIC_FORMATTYPE = 17;
	//private static final int ALERT_LEVEL_CHARACTERISTIC_OFFSET = 0;
	
	// Immediate alert levels
	private final static byte[] ALERT_LEVEL_LOW = {0x00};			// No alert
	private final static byte[] ALERT_LEVEL_MILD = {0x01};
	private final static byte[] ALERT_LEVEL_HIGH = {0x02};
	
	// Linkloss service
	//private final static UUID LINKLOSS_SERVICE_UUID = UUID.fromString("00001803-0000-1000-8000-00805f9b34fb");				// Service UUID
	
	// General callback variables
	private CallbackContext serverRunningCallbackContext = null;
	
	// Action Name Strings
	//private final String initializeActionName = "initialize";
	private final static String START_GATT_SERVER = "startServer";
	private final static String RESET_ALARM = "resetAlarm";		// Added 2017-01-13
	
	// Object keys
	private final static String keyStatus = "status";
	private final static String keyError = "error";
	private final static String keyMessage = "message";
	
	// Status Types
	private final static String statusServiceAdded = "serviceAdded";
	private final static String statusServiceExists = "serviceAlreadyProvided";
	private final static String statusWriteRequest = "characteristicWriteRequest";
	private final static String statusConnectionState = "serverConnectionState";
	private final static String statusAlarmReseted = "alarmReseted";	// Added 2017-01-13
	//private final String statusServerStopped = "scanStopped";
  
	// Error Types
	//private final String errorInitialize = "initialize";
	//private final static String errorStartServer = "startServer";
	private final static String errorConnectionState = "serverConnectionState";
	private final static String errorNoPermission = "noPermission"; // Added 2017-01-18
	//private final static String errorGattServer = "gattServer";	// Added 2016-01-14
	private final static String errorServerState = "serverState";	// Added 2016-01-19
	private final static String errorServerStateOff = "serverStateOff";
	private final static String errorServerStateUnsupported = "serverStateUnsupported";
	private final static String errorServerStateUnauthorized = "serverStateUnauthorized";	// iOS only
	private final static String errorServiceAdded = "serviceAdded"; // Added 2016-01-19
	private final static String errorWriteRequest = "writeRequest";		// Added 2017-01-10
	private final static String errorReadRequest = "readRequest";		// Added 2017-01-10

	
	// Error Messages
	private final static String logServerAlreadyRunning = "GATT server is already running";
	private final static String logNoPermission = "No permission granted for local notifications";	// Added 2017-01-18
	private final static String logService = "Immediate Alert service could not be added";
	private final static String logConnectionState = "Connection state changed with error";
	private final static String logStateUnsupported = "BLE is not supported by device";	// Added 2016-01-14
	private final static String logStatePoweredOff = "BLE is turned off for device";	// Added 2016-01-14
	private final static String logRequestNotSupported = "Request is not supported"; 	// Added 2017-01-10
	
	private boolean isInBackground = false;			// Added 2017-01-10
	private boolean iasInitialized = false; 		// Added 2017-01-18
	//private BluetoothGattServer gattServer;
	private BluetoothGattServer gattServer = null;		// Added 2016-01-19 instead of the line above
	//private BluetoothGattService immediateAlertService;
	
	// Bluetooth GATT interface callbacks
	private final BluetoothGattServerCallback mBluetoothGattServerCallback = new BluetoothGattServerCallback() {
		
		// Remote client characteristic write request
		@Override
		public void onCharacteristicWriteRequest(final BluetoothDevice device, final int requestId, final BluetoothGattCharacteristic characteristic, final boolean preparedWrite, final boolean responseNeeded, final int offset, final byte[] value) {
			//super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
			//characteristic.setValue(value);		// Removed 2017-01-10
			//JSONObject returnObj = new JSONObject();	// Added 2017-01-10 (removed 2017-01-13)
			
			//String sValue = "Write request: " + "value=" + String.valueOf((int)value[0]) + " offset=" + String.valueOf(offset);
			//showDebugMsgBox(sValue);	// Added 2017-01-13
			showDebugMsgBox("Write request: " + "value=" + String.valueOf((int)value[0]) + " offset=" + String.valueOf(offset));
			//showDebugMsgBox("Write request");
			
			if(characteristic.getUuid() ==  ALERT_LEVEL_CHAR_UUID){
				
			
			
				//characteristic.setValue(value);		// Added 2017-01-10 (removed 2017-01-13)
				/*try {		// Moved to alarm() 2017-01-10
					Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
					Ringtone r = RingtoneManager.getRingtone(cordova.getActivity().getApplicationContext(), notification);
					r.play();
				} catch (Exception e) {

				}*/
				//super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
				
				if(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) != value[0]){	// If statement and it's code block added 2017-01-13
					// There is an alarm
					// After connecting to the clip, the clip first sends "No Alert" (sometimes twice). This must not be interpreted as an alarm.
					// After that, the clip sends toggled alert levels when there are alarms (that is, alternating high and no alert level). 
					characteristic.setValue(value);		// Set the value of the characteristic to the new alert level
					alarm(parseCharacteristicValue(characteristic), device.getAddress());
				}
				
				
				/*alarm();		// Code block removed 2017-01-13
				//Notify user of started server and save callback
				addProperty(returnObj, keyStatus, statusWriteRequest);
				addProperty(returnObj, "device", device.getAddress());
				addProperty(returnObj, "characteristic", characteristic.getUuid().toString());
				addProperty(returnObj, "value", parseCharacteristicValue(characteristic));
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
				pluginResult.setKeepCallback(true);					// Save the callback so it can be invoked several times
				serverRunningCallbackContext.sendPluginResult(pluginResult);*/
				
				if (responseNeeded)	// If and it's code block added 2017-01-10
					gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
			}
			else {		// else and it's code block added 2017-01-10
				if (responseNeeded)
					gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
				
				JSONObject returnObj = new JSONObject();	// Added 2017-01-13
				addProperty(returnObj, keyError, errorWriteRequest);
				addProperty(returnObj, keyMessage, logRequestNotSupported);
				PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
				pluginResult.setKeepCallback(true);
				serverRunningCallbackContext.sendPluginResult(pluginResult);
			}
			/*if (responseNeeded)		// Removed 2017-01-10
				gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);*/
		}
		
		@Override
		public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
			//Callback indicating when GATT client has connected/disconnected to/from a remote GATT server
			
			JSONObject returnObj = new JSONObject();
			// Notify user of connection status change
			if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {
				addProperty(returnObj, keyStatus, statusConnectionState);
				addProperty(returnObj, "device", device.getAddress());
				addProperty(returnObj, "state", "connected");
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
				pluginResult.setKeepCallback(true);														// Save the callback so it can be invoked several times
				serverRunningCallbackContext.sendPluginResult(pluginResult);
			}
			else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
				addProperty(returnObj, keyStatus, statusConnectionState);
				addProperty(returnObj, "device", device.getAddress());
				addProperty(returnObj, "state", "disconnected");
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
				pluginResult.setKeepCallback(true);														// Save the callback so it can be invoked several times
				serverRunningCallbackContext.sendPluginResult(pluginResult);
			}
			else {
				addProperty(returnObj, keyError, errorConnectionState);
				addProperty(returnObj, keyMessage, logConnectionState + " " + status);
				PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
				//pluginResult.setKeepCallback(true);
				pluginResult.setKeepCallback(false);
				serverRunningCallbackContext.sendPluginResult(pluginResult);
				serverRunningCallbackContext = null;
			}
		}

		@Override
		public void onServiceAdded(int status, BluetoothGattService service) {
			
			showDebugMsgBox("onServiceAdded called!");
			
			JSONObject returnObj = new JSONObject();
			// If statement below added 2016-01-19 for testing
			if(status != BluetoothGatt.GATT_SUCCESS){
				// Notify user of error
				addProperty(returnObj, keyError, errorServiceAdded);
				addProperty(returnObj, keyMessage, logService);
				PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
				pluginResult.setKeepCallback(false);
				serverRunningCallbackContext.sendPluginResult(pluginResult);
				serverRunningCallbackContext = null;
				//return;
			}
			else {
				// Notify user and save callback
				addProperty(returnObj, keyStatus, statusServiceAdded);
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
				pluginResult.setKeepCallback(true);					// Save the callback so it can be invoked several times
				serverRunningCallbackContext.sendPluginResult(pluginResult);	
			}
			
		}

		@Override
		public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
			// Not supported/implemented
			
			JSONObject returnObj = new JSONObject();		// Added 2017-01-10
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);	// Added 2017-01-10
			
			addProperty(returnObj, keyError, errorReadRequest);	// Added 2017-01-10
			addProperty(returnObj, keyMessage, logRequestNotSupported);	// Added 2017-01-10
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);	// Added 2017-01-10
			pluginResult.setKeepCallback(true);	// Added 2017-01-10
			serverRunningCallbackContext.sendPluginResult(pluginResult);	// Added 2017-01-10
		
		}
			

		@Override
		public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
			// Not supported
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
		}

		@Override
		public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
			// Not supported
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
		}

		@Override
		public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
			// Not supported
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null);
		}
	};
	
	//Actions
	@Override
	public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException
	{
		try {
			if (START_GATT_SERVER.equals(action)) { 
				startServerAction(callbackContext);
				return true;
			}
			else if (RESET_ALARM.equals(action)){
				resetAlarmAction(callbackContext);
				return true;
			}
			else if (action.equals("alarm")){
				alarmAction(callbackContext);
				return true;
			}
			callbackContext.error("Invalid action");
			return false;
		} 
		catch(Exception ex) {
			System.err.println("Exception: " + ex.getMessage());
			callbackContext.error(ex.getMessage());
			return false;
		} 
	}
	
	private void startServerAction(CallbackContext callbackContext)
	{
		
		JSONObject returnObj = new JSONObject();
		
		// If statement below added 2016-01-19 (moved up here 2016-01-21)
		if(BluetoothAdapter.getDefaultAdapter() == null){
		    	// Device does not support Bluetooth, notify user of unsupported Bluetooth
			addProperty(returnObj, keyError, errorServerState);
			addProperty(returnObj, keyMessage, logStateUnsupported);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
			pluginResult.setKeepCallback(false);
			callbackContext.sendPluginResult(pluginResult);
			return;
		} 
		else {
			if(!BluetoothAdapter.getDefaultAdapter().isEnabled()){
			        // Bluetooth is not enabled, notify user that Bluetooth is not enabled
				addProperty(returnObj, keyError, errorServerState);
				addProperty(returnObj, keyMessage, logStatePoweredOff);
				PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
				pluginResult.setKeepCallback(false);
				callbackContext.sendPluginResult(pluginResult);
				return;
		    	}
		}
		
		if(!NotificationManagerCompat.from(cordova.getActivity().getApplicationContext()).areNotificationsEnabled()){	// If statement and its code block added 2017-01-18
			// areNotificationsEnabled() from the support library returns true if notifications are enabled for
			// the app and if API >= 19. If Api < 19 it will always return true (even if notifications actually
			// are disabled for the app).
			JSONObject returnJsonObj = new JSONObject();
			addProperty(returnJsonObj, keyError, errorNoPermission);
			addProperty(returnJsonObj, keyMessage, logNoPermission);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnJsonObj);
			pluginResult.setKeepCallback(true);		// Save the callback so it can be invoked several times
			callbackContext.sendPluginResult(pluginResult);
			// return;
		}
		// If GATT server has been initialized or the GATT server is already running, don't start it again
		if((gattServer != null) && (serverRunningCallbackContext != null))
		{
			addProperty(returnObj, keyStatus, statusServiceExists);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
			pluginResult.setKeepCallback(true);					// Save the callback so it can be invoked several times
			serverRunningCallbackContext.sendPluginResult(pluginResult);
			iasInitialized = false; 				// Added 2017-01-18
			//callbackContext.error(returnObj);
			//serverRunningCallbackContext.error(returnObj);	// Added 7/8 instead of line above
			return;
		}
		
		// Open a GATT server if not already opened
		final BluetoothManager bluetoothManager = (BluetoothManager) cordova.getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
		if(gattServer == null)
			gattServer = bluetoothManager.openGattServer(cordova.getActivity().getApplicationContext(), mBluetoothGattServerCallback);
		if(gattServer == null){		// If statement added 2016-01-14
			// Notify user of unsupported Bluetooth Smart
			addProperty(returnObj, keyError, errorServerState);
			addProperty(returnObj, keyMessage, logStateUnsupported);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
			pluginResult.setKeepCallback(false);
			callbackContext.sendPluginResult(pluginResult);
			return;
		}
		
		// Create an Immediate Alert service if not already provided by the device
		final BluetoothGattService immediateAlertService = new BluetoothGattService(IMMEDIATE_ALERT_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
		if(gattServer.getService(IMMEDIATE_ALERT_SERVICE_UUID) == null){
			final BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(ALERT_LEVEL_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE);
			//characteristic.setValue(ALERT_LEVEL_CHARACTERISTIC_VALUE, ALERT_LEVEL_CHARACTERISTIC_FORMATTYPE, ALERT_LEVEL_CHARACTERISTIC_OFFSET);
			//characteristic.setValue(ALERT_LEVEL_HIGH);	// Removed 2017-01-13
			characteristic.setValue(ALERT_LEVEL_LOW);	// Added 2017-01-13
			if(!immediateAlertService.addCharacteristic(characteristic)){
				// Notify user of error
				addProperty(returnObj, keyError, errorServiceAdded);
				addProperty(returnObj, keyMessage, logService);
				PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
				pluginResult.setKeepCallback(false);
				callbackContext.sendPluginResult(pluginResult);
				return;	
			}
		}
		else {
			// Notify user of added service(s) and save callback context
			serverRunningCallbackContext = callbackContext;
			addProperty(returnObj, keyStatus, statusServiceExists);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
			pluginResult.setKeepCallback(true);		// Save the callback so it can be invoked several times
			//callbackContext.sendPluginResult(pluginResult);
			serverRunningCallbackContext.sendPluginResult(pluginResult);
			return;
		}
		
		//Save the callback context for setting up GATT server
		serverRunningCallbackContext = callbackContext;
		
		// Add Immediate Alert service (this will call the implementation od the onServiceAdded callback)
		gattServer.addService(immediateAlertService); 
	}
	
	private void resetAlarmAction(CallbackContext callbackContext)		// Function added 2017-01-17
	{
		// Resets the Immediate Alert Service initialized flag.
		// Should be called after a client has disconnected since when a nRF8002 module connects to the GATT server running
		// Immediate Alert Service, it writes it's current alert level (always "No Alert", that is alert level 0). This must not be interpreted as an alert.
		
		final BluetoothGattService iaService = gattServer.getService(IMMEDIATE_ALERT_SERVICE_UUID);
		if (iaService != null){
			final BluetoothGattCharacteristic alertLevelChar = iaService.getCharacteristic(ALERT_LEVEL_CHAR_UUID);
			if (alertLevelChar != null)
				alertLevelChar.setValue(ALERT_LEVEL_LOW);
		}
			
		//Notify user of reseted alarm
		JSONObject returnObj = new JSONObject();
		addProperty(returnObj, keyStatus, statusAlarmReseted);
		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
		pluginResult.setKeepCallback(false);
		callbackContext.sendPluginResult(pluginResult);
	}

	//private void alarm(){		// Removed 2017-01-13
	private void alarm(final String alertLevel, final String deviceUUID){		// Added 2017-01-13
		
		if (isInBackground) {
			// Show local notification only if the app is in the background
			long[] pattern = { 0, 200, 500 };
			//NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
			NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(cordova.getActivity().getApplicationContext())
			.setContentTitle("SenseSoft Notifications Mini")
			.setContentText("Incoming SenseSoft Mini alarm!")
			//.setSmallIcon(R.drawable.screen_background_dark)
			.setSmallIcon(cordova.getActivity().getApplicationContext().getApplicationInfo().icon)
			.setPriority(NotificationCompat.PRIORITY_MAX)
			//.setAutoCancel(true)
			.setCategory(NotificationCompat.CATEGORY_ALARM)
			.setGroup("SENSESOFT_MINI")
			.setTicker("SenseSoft Mini")
			.setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_LIGHTS)
			.setVibrate(pattern);
			//.setFullScreenIntent(PendingIntent intent, boolean highPriority)
			//.setSound(Uri sound, STREAM_ALARM);

			//NotificationManager mNotificationManager = (NotificationManager) Context.getSystemService(Context.NOTIFICATION_SERVICE);
			NotificationManager mNotificationManager = (NotificationManager) cordova.getActivity().getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
			// mId allows you to update the notification later on.
			mNotificationManager.notify(1665, mBuilder.build());
		}
		else {		// else statement and its code block added 2017-01-10
			// Manually play sound if app is in the foreground
			try {
				Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
				Ringtone r = RingtoneManager.getRingtone(cordova.getActivity().getApplicationContext(), notification);
				r.play();
			} catch (Exception e) {
				// Do nothing
			}
		}
		
		// Section added 2017-01-13
		//Notify user of started server and save callback
		JSONObject returnObj = new JSONObject();
		addProperty(returnObj, keyStatus, statusWriteRequest);
		//addProperty(returnObj, "device", device.getAddress());
		addProperty(returnObj, "device", deviceUUID);
		//addProperty(returnObj, "characteristic", characteristic.getUuid().toString());
		addProperty(returnObj, "characteristic", ALERT_LEVEL_CHAR_UUID.toString());
		//addProperty(returnObj, "value", parseCharacteristicValue(characteristic));
		addProperty(returnObj, "value", alertLevel);
		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
		pluginResult.setKeepCallback(true);					// Save the callback so it can be invoked several times
		serverRunningCallbackContext.sendPluginResult(pluginResult);
		// End section added 2017-01-13
	}
	private void alarmAction(CallbackContext callbackContext)
	{
		// Action function just to test local notifications from outside the plugin
		// Show local notification
		long[] pattern = { 0, 200, 500 };
		//NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(cordova.getActivity().getApplicationContext())
	        .setContentTitle("SenseSoft Notifications Mini")
	        .setContentText("Incoming SenseSoft Mini alarm!")
	        //.setSmallIcon(R.drawable.screen_background_dark)
	        .setSmallIcon(cordova.getActivity().getApplicationContext().getApplicationInfo().icon)
	        .setPriority(NotificationCompat.PRIORITY_MAX)
	        //.setAutoCancel(true)
	        .setCategory(NotificationCompat.CATEGORY_ALARM)
	        .setGroup("SENSESOFT_MINI")
	        .setTicker("SenseSoft Mini")
	        .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_LIGHTS)
	        .setVibrate(pattern);
	        //.setFullScreenIntent(PendingIntent intent, boolean highPriority)
	        //.setSound(Uri sound, STREAM_ALARM);
		
		//NotificationManager mNotificationManager = (NotificationManager) Context.getSystemService(Context.NOTIFICATION_SERVICE);
		NotificationManager mNotificationManager = (NotificationManager) cordova.getActivity().getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
		// mId allows you to update the notification later on.
		mNotificationManager.notify(1665, mBuilder.build());
	}
  
	  private void addProperty(JSONObject obj, String key, Object value)
	  {
			try
			{
			  obj.put(key, value);
			}
			catch (JSONException e)
			{ /* Ignore */ }
	  }
  
	private String parseCharacteristicValue(final BluetoothGattCharacteristic characteristic)
	{
		if (characteristic == null)
			return "";

		if (characteristic.getUuid() == ALERT_LEVEL_CHAR_UUID) {
			final int value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
			switch (value) {
				case 0:
					return "No Alert";
				case 1:
					return "Mild Alert";
				case 2:
					return "High Alert";
				default:
					return "Parse Error";
			}
		}	
		else	
			return characteristic.getStringValue(0);
	}
	
	private synchronized void showDebugMsgBox(final String message)		// Added 2017-01-13
	{
		Runnable runnable = new Runnable() {
            		public void run() {
				AlertDialog.Builder debugAlert  = new AlertDialog.Builder(cordova.getActivity());
				debugAlert.setMessage(message);
				debugAlert.setTitle("Debug SSNM");
				debugAlert.setCancelable(false);
				debugAlert.setNeutralButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.dismiss();
					}
				});
				debugAlert.create().show();
           		 };
		};
		cordova.getActivity().runOnUiThread(runnable);	// Run it on the ui thread as cordova plugins runs on the WebCore thread (also the plugin's JavaScript runs on the WebCore thread).
	}
	
	/*private boolean isInBackground() {	// Added 2017-01-09
		
		// Checks if the app is in the background
		
		boolean inBackground = true;
		
		ActivityManager activityManager = (ActivityManager) cordova.getActivity().getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            		List<ActivityManager.RunningAppProcessInfo> runningProcesses = activityManager.getRunningAppProcesses();
            		for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                		if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    			for (String activeProcess : processInfo.pkgList) {
                        			if (activeProcess.equals(cordova.getActivity().getApplicationContext().getPackageName())) {
                            				inBackground = false;
                       				 }
                    			}
               			 }
           		 }
       		}
		else {
			List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
			if (tasks.get(0).topActivity.getPackageName().equalsIgnoreCase(cordova.getActivity().getApplicationContext().getPackageName()))
				inBackground = false;
		}
		
		return inBackground;
	}*/
	
	// Plugin initialize method for any start-up logic (see https://cordova.apache.org/docs/en/5.0.0/guide/platforms/android/plugin.html)
	/*@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    		super.initialize(cordova, webView);
    		// your init code here
	}*/
	
	/*****************************************************************************************************
	* Cordova Plugin (see CordovaPlugin.java)
	*****************************************************************************************************/
	
	@Override
	 protected void pluginInitialize() {
	 	// Called after plugin construction and fields have been initialized
	 	super.pluginInitialize();
		isInBackground = false;		// App is in foreground (added 2017-01-10)
		showDebugMsgBox("pluginInitialize() called!");	// Added 2017-01-10
		/*AlertDialog.Builder debugAlert  = new AlertDialog.Builder(cordova.getActivity());
		debugAlert.setMessage("pluginInitialize() called!");
		debugAlert.setTitle("Debug SSNM");
		debugAlert.setCancelable(false);
		debugAlert.setNeutralButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
			}
		});
		debugAlert.create().show();*/
	 }
	
	/*@Override
	public void onDestroy() {
		 // The final call you receive before your activity is destroyed
		super.onDestroy();
	}*/
	
	/*@Override
	 public void onStart() {
		 // Called when the activity is becoming visible to the user
		 super.onStart();
    	}*/
	/*@Override
	 public void onStop() {
		 // Called when the activity is no longer visible to the user
		 super.onStop();
   	 }*/
	
	@Override
	public void onPause(boolean multitasking) {
		// Called when the system is about to start resuming a previous activity
		isInBackground = true;		// App is put in background (added 2017-01-10)
		super.onPause(multitasking);
		showDebugMsgBox("onPause() called!");	// Added 2017-01-10
		/*AlertDialog.Builder debugAlert  = new AlertDialog.Builder(cordova.getActivity());
		debugAlert.setMessage("onPause(...) called!");
		debugAlert.setTitle("Debug SSNM");
		debugAlert.setCancelable(false);
		debugAlert.setNeutralButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
			}
		});
		debugAlert.create().show();*/
    	}
	
	@Override
	public void onResume(boolean multitasking) {
		// Called when the activity will start interacting with the user
		isInBackground = false;		// App is put in foreground (added 2017-01-10)
		super.onResume(multitasking);
		showDebugMsgBox("onResume() called!");	// Added 2017-01-10
		/*AlertDialog.Builder debugAlert  = new AlertDialog.Builder(cordova.getActivity());
		debugAlert.setMessage("onResume(...) called!");
		debugAlert.setTitle("Debug SSNM");
		debugAlert.setCancelable(false);
		debugAlert.setNeutralButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
			}
		});
		debugAlert.create().show();*/
    	}
	
	/*@Override
    	public void onReset() {
		// Called when the WebView does a top-level navigation or refreshes
		// Plugins should stop any long-running processes and clean up internal state
		super.onReset();
	}*/
}
