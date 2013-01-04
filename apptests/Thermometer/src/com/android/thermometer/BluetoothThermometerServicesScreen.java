/*
 *  Copyright (c) 2011-12, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *        * Redistributions of source code must retain the above copyright
 *          notice, this list of conditions and the following disclaimer.
 *        * Redistributions in binary form must reproduce the above copyright
 *          notice, this list of conditions and the following disclaimer in the
 *          documentation and/or other materials provided with the distribution.
 *        * Neither the name of The Linux Foundation nor
 *          the names of its contributors may be used to endorse or promote
 *          products derived from this software without specific prior written
 *          permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.    IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.thermometer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.IBluetoothThermometerCallBack;
import android.bluetooth.IBluetoothThermometerServices;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.text.Editable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class BluetoothThermometerServicesScreen extends Activity {

	public final static String TAG = "BluetoothThermometerServicesScreen";

	public static HashMap<String, ParcelUuid> charUUIDMap;

	public static HashMap<ParcelUuid, String> UUIDNameMap;

	private IBluetoothThermometerServices thermoservice = null;

	private String currentServiceName = null;

	private static Button buttonClearNotify;

	private static Button buttonRead;

	private static Button buttonWrite;

	private static Button buttonNotify;

	private static EditText readText;

	private static EditText writeValueText;

	private static EditText statusText;

	private static Spinner spinner = null;

	public static boolean serviceReady = false;

	public static boolean deviceServiceReady = false;

	private static ParcelUuid selectedCharUUID;

	private static Context myContext = null;

	private final static int UPDATE_STATUS = 0;

	private final static int UPDATE_READ = 1;

	private final static int SERVICE_READY = 2;

	private final static int SERVICE_CHANGE = 3;

	private static long count = 0;

	private final static String ARG = "arg";

	public static final String INTERMEDIATE_TEMPERATURE_UUID = "00002a1e00001000800000805f9b34fb";

	public static final String THERMOMETER_SERVICE_OPERATION = "THERMOMETER_SERVICE_OPERATION";

	public static final String THERMOMETER_SERVICE_OP_SERVICE_READY = "THERMOMETER_SERVICE_OP_SERVICE_READY";

	public static final String THERMOMETER_SERVICE_DISCOVER_PRIMARY = "THERMOMETER_SERVICE_DISCOVER_PRIMARY";

	public static final String THERMOMETER_SERVICE_OP_READ_VALUE = "THERMOMETER_SERVICE_OP_READ";

	public static final String THERMOMETER_SERVICE_OP_STATUS = "THERMOMETER_SERVICE_OP_STATUS";

	public static final String THERMOMETER_SERVICE_OP_VALUE = "THERMOMETER_SERVICE_OP_VALUE";

	public static final String THERMOMETER_SERVICE_CHANGE = "THERMOMETER_SERVICE_CHANGE";

	public static final String THERMOMETER_SERVICE_OP_WRITE_VALUE = "THERMOMETER_SERVICE_OP_WRITE_VALUE";

	public static final String THERMOMETER_SERVICE_OP_REGISTER_NOTIFY_INDICATE = "THERMOMETER_SERVICE_OP_REGISTER_NOTIFY_INDICATE";

	public static final String THERMOMETER_SERVICE_CHAR_UUID = "THERMOMETER_SERVICE_CHAR_UUID";

	public static final String THERMOMETER_SERVICE_NOTIFICATION_INDICATION_VALUE = "THERMOMETER_SERVICE_NOTIFICATION_INDICATION_VALUE";

	public static List<String> thermometerProfileUUID = Arrays.asList(
			"0000180900001000800000805f9b34fb",
			"0000180a00001000800000805f9b34fb");

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.thermohealthscreen);
		myContext = getApplicationContext();

		Log.d(TAG, "In the thermometer health services screen");
		thermoservice = BluetoothThermometerClient.bluetoothThermometerServices;

		readText = (EditText) findViewById(R.id.readText);
		writeValueText = (EditText) findViewById(R.id.writeValueText);
		statusText = (EditText) findViewById(R.id.statusText);

		buttonRead = (Button) findViewById(R.id.readButton);
		buttonRead.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.d(TAG, "buttonRead clicked");
				try {
						thermoservice
								.readCharacteristicsValue(selectedCharUUID);
				} catch (RemoteException e) {
					Log.e(TAG, " Character could not be read" + e.getMessage());
				}

			}
		});

		buttonWrite = (Button) findViewById(R.id.writeButton);
		buttonWrite.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.d(TAG, "buttonWrite clicked");
				try {
					String val = null;
					Editable edText = writeValueText.getText();
					if (edText != null) {
						val = edText.toString();
						Log.d(TAG, "Value to write : " + val);
					}
					if (val != null) {
						Log.d(TAG, "Calling thermo service write for uuid : "
								+ selectedCharUUID + " value : " + val);
							boolean result = thermoservice
									.writeCharacteristicsValue(
											selectedCharUUID, val);
							if (!result) {
								statusText.setText("write failed");
							}
					}

				} catch (RemoteException e) {
					Log.e(TAG, " Character could not be writen");
				}

			}
		});

		buttonNotify = (Button) findViewById(R.id.notificationButton);
		buttonNotify.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.d(TAG, "button Notify / indicate clicked");
				try {
						boolean result = thermoservice
								.notifyIndicateValue(selectedCharUUID);
						if (!result) {
							statusText.setText("set notify failed");
						}

				} catch (RemoteException e) {
					Log.e(TAG, " set notify failed");
				}

			}
		});

		buttonClearNotify = (Button) findViewById(R.id.clearNotify);
		buttonClearNotify.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.d(TAG, "button clear Notify / indicate clicked");
				try {
						boolean result = thermoservice
								.clearNotifyIndicate(selectedCharUUID);
						if (!result) {
							statusText.setText("clear notify failed");
						}
				} catch (RemoteException e) {
					Log.e(TAG, " clear notify failed");
				}

			}
		});

		Intent intent = getIntent();
		currentServiceName = intent
				.getStringExtra(BluetoothThermometerClient.GATT_SERVICE_NAME);
		Log.d(TAG, "Service Name : " + currentServiceName);
		Log.d(TAG, "Health Service ready : " + serviceReady);
		Log.d(TAG, "Device Service ready : " + deviceServiceReady);

		spinner = (Spinner) findViewById(R.id.spinner);
		ArrayAdapter<CharSequence> adapter = null;
		if (currentServiceName
				.equals(BluetoothThermometerClient.GATT_SERVICE_HEALTH_SERVICE)) {
			Log.d(TAG, "Add resources for  : " + "GATT_SERVICE_HEALTH_SERVICE");
			adapter = ArrayAdapter.createFromResource(this,
					R.array.characteristics_array,
					android.R.layout.simple_spinner_item);
			if (!serviceReady) {
				disableButtons();
			}
		} else {
			Log.d(TAG, "Add resources for  : "
					+ "GATT_SERVICE_DEVICE_INFO_SERVICE");
			adapter = ArrayAdapter.createFromResource(this,
					R.array.device_characteristics_array,
					android.R.layout.simple_spinner_item);
			if (!deviceServiceReady) {
				disableButtons();
			}
		}

		charUUIDMap = new HashMap<String, ParcelUuid>();
		UUIDNameMap = new HashMap<ParcelUuid, String>();
		populateCharUUIDMap();
		populateUUIDNameMap();
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(new OnItemSelectedListener() {


			public void onItemSelected(AdapterView<?> parentView,
					View selectedItemView, int position, long id) {
				Log.d(TAG, "Selected an item in the list");
					String selectedChar = parentView
							.getItemAtPosition(position).toString();
					Log.d(TAG, "selected character : " + selectedChar);
					selectedCharUUID = charUUIDMap.get(selectedChar);
					Log.d(TAG, "selected ParcelUUID : " + selectedCharUUID);

			}

			public void onNothingSelected(AdapterView<?> parentView) {

			}

		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.servicescreenmenu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.closeService:
			closeGattService();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		clearUI();
	}

	@Override
	public void onStop() {
		super.onStop();
		clearUI();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		clearUI();
	}

	private void closeGattService() {
		if (currentServiceName
				.equals(BluetoothThermometerClient.GATT_SERVICE_HEALTH_SERVICE)) {
			try {
				thermoservice
						.closeThermometerService(
								BluetoothThermometerClient.RemoteDevice,
								new ParcelUuid(
										convertUUIDStringToUUID(BluetoothThermometerClient.StringServicesUUID[0])));
				serviceReady = false;
			} catch (RemoteException e) {
				Log.e(TAG, "Error while closing the service : "
						+ BluetoothThermometerClient.StringServicesUUID[0]);

				e.printStackTrace();
			}
		} else if (currentServiceName
				.equals(BluetoothThermometerClient.GATT_SERVICE_DEVICE_INFO_SERVICE)) {
			try {
				thermoservice
						.closeThermometerService(
								BluetoothThermometerClient.RemoteDevice,
								new ParcelUuid(
										convertUUIDStringToUUID(BluetoothThermometerClient.StringServicesUUID[1])));
				deviceServiceReady = false;
			} catch (RemoteException e) {
				Log.e(TAG, "Error while closing the service : "
						+ BluetoothThermometerClient.StringServicesUUID[1]);

				e.printStackTrace();
			}
		}
	}


	private void clearUI() {
		statusText.setText("");
		readText.setText("");
		writeValueText.setText("");
	}

	private static void enableButtons() {
		buttonWrite.setEnabled(true);
		buttonWrite.setClickable(true);
		buttonRead.setEnabled(true);
		buttonRead.setClickable(true);
		buttonNotify.setEnabled(true);
		buttonNotify.setClickable(true);
		buttonClearNotify.setEnabled(true);
		buttonClearNotify.setClickable(true);
	}

	private static void disableButtons() {
		buttonWrite.setEnabled(false);
		buttonWrite.setClickable(false);
		buttonRead.setEnabled(false);
		buttonRead.setClickable(false);
		buttonNotify.setEnabled(false);
		buttonNotify.setClickable(false);
		buttonClearNotify.setEnabled(false);
		buttonClearNotify.setClickable(false);
	}

	public static final Handler msgHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Log.d(TAG, "Inside handleMessage");
			switch (msg.what) {
			case UPDATE_STATUS:
				Log.d(TAG, "UPDATE_STATUS_TEXT");
				String status = msg.getData().getString(ARG);
				statusText.setText(status);
				break;
			case UPDATE_READ:
				Log.d(TAG, "UPDATE_READ_TEXT");
				String read = msg.getData().getString(ARG);
				readText.setText(read);
				break;
			case SERVICE_READY:
				Log.d(TAG, "SERVICE_READY");
				String uuidStr = msg.getData().getString(ARG);

				if (uuidStr
						.equals(new ParcelUuid(
								convertUUIDStringToUUID(BluetoothThermometerClient.StringServicesUUID[0]))
								.toString())) {
					serviceReady = true;
				} else {
					deviceServiceReady = true;
				}
				enableButtons();
				break;
			case SERVICE_CHANGE:
				Log.d(TAG, "SERVICE_CHANGE");
				serviceReady = false;
				deviceServiceReady = false;
				disableButtons();
				Toast.makeText(myContext, "Service Change Recvd",
						Toast.LENGTH_SHORT).show();
				break;
			default:
				break;
			}
		}
	};

	private void populateCharUUIDMap() {
		charUUIDMap.put("Date Time", new ParcelUuid(
				convertUUIDStringToUUID("00002a0800001000800000805f9b34fb")));
		charUUIDMap.put("Measurement Interval", new ParcelUuid(
				convertUUIDStringToUUID("00002a2100001000800000805f9b34fb")));
		charUUIDMap.put("Temperature Type", new ParcelUuid(
				convertUUIDStringToUUID("00002a1d00001000800000805f9b34fb")));
		charUUIDMap.put("Temperature Measurement", new ParcelUuid(
				convertUUIDStringToUUID("00002a1c00001000800000805f9b34fb")));
		charUUIDMap.put("Intermediate Temperature", new ParcelUuid(
				convertUUIDStringToUUID("00002a1e00001000800000805f9b34fb")));

		charUUIDMap.put("Manufacturer Name", new ParcelUuid(
				convertUUIDStringToUUID("00002a2900001000800000805f9b34fb")));
		charUUIDMap.put("Model Number", new ParcelUuid(
				convertUUIDStringToUUID("00002a2400001000800000805f9b34fb")));
		charUUIDMap.put("Serial Number", new ParcelUuid(
				convertUUIDStringToUUID("00002a2500001000800000805f9b34fb")));
		charUUIDMap.put("Hardware Revision", new ParcelUuid(
				convertUUIDStringToUUID("00002a2700001000800000805f9b34fb")));
		charUUIDMap.put("Software Revision", new ParcelUuid(
				convertUUIDStringToUUID("00002a2800001000800000805f9b34fb")));
		charUUIDMap.put("Firmware Revision", new ParcelUuid(
				convertUUIDStringToUUID("00002a2600001000800000805f9b34fb")));
		charUUIDMap.put("System Id", new ParcelUuid(
				convertUUIDStringToUUID("00002a2300001000800000805f9b34fb")));
		charUUIDMap.put("Certification Data", new ParcelUuid(
				convertUUIDStringToUUID("00002a2a00001000800000805f9b34fb")));
	}

	private void populateUUIDNameMap() {
		UUIDNameMap.put(new ParcelUuid(
				convertUUIDStringToUUID("00002a0800001000800000805f9b34fb")),"Date Time");
		UUIDNameMap.put(new ParcelUuid(
				convertUUIDStringToUUID("00002a2100001000800000805f9b34fb")),
				"Measurement Interval");
		UUIDNameMap.put(new ParcelUuid(
				convertUUIDStringToUUID("00002a1d00001000800000805f9b34fb")),
				"Temperature Type");
		UUIDNameMap.put(new ParcelUuid(
				convertUUIDStringToUUID("00002a1c00001000800000805f9b34fb")),
				"Temperature Measurement");
		UUIDNameMap.put(new ParcelUuid(
				convertUUIDStringToUUID("00002a1e00001000800000805f9b34fb")),
				"Intermediate Temperature");

		UUIDNameMap.put(new ParcelUuid(
				convertUUIDStringToUUID("00002a2900001000800000805f9b34fb")),
				"Manufacturer Name");
		UUIDNameMap.put(new ParcelUuid(
				convertUUIDStringToUUID("00002a2400001000800000805f9b34fb")),
				"Model Number");
		UUIDNameMap.put(new ParcelUuid(
				convertUUIDStringToUUID("00002a2500001000800000805f9b34fb")),
				"Serial Number");
		UUIDNameMap.put(new ParcelUuid(
				convertUUIDStringToUUID("00002a2700001000800000805f9b34fb")),
				"Hardware Revision");
		UUIDNameMap.put(new ParcelUuid(
				convertUUIDStringToUUID("00002a2800001000800000805f9b34fb")),
				"Software Revision");
		UUIDNameMap.put(new ParcelUuid(
				convertUUIDStringToUUID("00002a2600001000800000805f9b34fb")),
				"Firmware Revision");
		UUIDNameMap.put(new ParcelUuid(
				convertUUIDStringToUUID("00002a2300001000800000805f9b34fb")),
				"System Id");
		UUIDNameMap.put(new ParcelUuid(
				convertUUIDStringToUUID("00002a2a00001000800000805f9b34fb")),
				"Certification Data");
	}

	public static UUID convertUUIDStringToUUID(String UUIDStr) {
		if (UUIDStr.length() != 32) {
			return null;
		}
		String uuidMsB = UUIDStr.substring(0, 16);
		String uuidLsB = UUIDStr.substring(16, 32);

		if (uuidLsB.equals("800000805f9b34fb")) {
			// TODO Long is represented as two complement. Fix this later.
			UUID uuid = new UUID(Long.valueOf(uuidMsB, 16), 0x800000805f9b34fbL);
			return uuid;
		} else {
			UUID uuid = new UUID(Long.valueOf(uuidMsB, 16),
					Long.valueOf(uuidLsB));
			return uuid;
		}
	}

	public static IBluetoothThermometerCallBack mCallback = new IBluetoothThermometerCallBack.Stub() {

		public void sendResult(Bundle arg0) throws RemoteException {
			Log.d(TAG, "%%%%%%%%%******IRemoteCallback sendResult callback");
			ParcelUuid Uuid = arg0
.getParcelable(THERMOMETER_SERVICE_CHAR_UUID);
			Log.d(TAG, "%%%%%%%%% Uuid");
			String charName = UUIDNameMap.get(Uuid);
			Log.d(TAG, "Bundle arg1 : " + Uuid + " Name : " + charName);
			String op = arg0.getString(THERMOMETER_SERVICE_OPERATION);
			Log.d(TAG, "Bundle arg2 : " + op);
			boolean status = arg0.getBoolean(THERMOMETER_SERVICE_OP_STATUS);
			Log.d(TAG, "Bundle arg3 : " + status);
			ArrayList<String> values = arg0
					.getStringArrayList(THERMOMETER_SERVICE_OP_VALUE);
			String result = "";
			for (String value : values) {
				result = result + value + " ";
				Log.d(TAG, "Bundle arg4 : " + value);
			}

			Message msg = new Message();
			Bundle b = new Bundle();

			if (op.equals(THERMOMETER_SERVICE_OP_SERVICE_READY)) {
				Log.d(TAG, " THERMOMETER_SERVICE_OP_SERVICE_READY");
				String text = Uuid.toString();
				if (status) {
					sendMsg(SERVICE_READY, text, msg, b);
				} else {
					Log.e(TAG, "Error while service ready");
				}

			} else if (op.equals(THERMOMETER_SERVICE_CHANGE)) {
				Log.d(TAG, " THERMOMETER_SERVICE_CHANGE");
				String text = "";
				if (status) {
					Log.d(TAG, "Sending SERVICE_CHANGE msg ");
					sendMsg(SERVICE_CHANGE, text, msg, b);
				} else {
					Log.d(TAG, "THERMOMETER_SERVICE_CHANGE status is false ");
				}

			} else if (op.equals(THERMOMETER_SERVICE_OP_READ_VALUE)) {
				Log.d(TAG, " THERMOMETER_SERVICE_OP_READ_VALUE");
				String text = charName + " read : " + status;
				if (!status) {
					sendMsg(UPDATE_STATUS, text, msg, b);
				} else {
					sendMsg(UPDATE_READ, result, msg, b);
				}

			} else if (op.equals(THERMOMETER_SERVICE_OP_WRITE_VALUE)) {
				Log.d(TAG, " THERMOMETER_SERVICE_OP_WRITE_VALUE");
				String text = charName + " write : " + status;
				sendMsg(UPDATE_STATUS, text, msg, b);
			} else if (op
					.equals(THERMOMETER_SERVICE_NOTIFICATION_INDICATION_VALUE)) {
				Log.d(TAG, " THERMOMETER_SERVICE_OP_WRITE_VALUE");
				String text = charName + " : " + result;
				sendMsg(UPDATE_STATUS, text, msg, b);
			}
		}

		private void sendMsg(int msgWhat, String result, Message msg,
				Bundle b) {
			Log.d(TAG, "Inside sendMsg");
			msg.what = msgWhat;
			b.putString(ARG, result);
			msg.setData(b);
			msgHandler.sendMessage(msg);
		}

	};

}
