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

package com.android.le.GattClientTestApp;

import android.app.Activity;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class CharacteristicScreen extends Activity {

	public final String TAG = "characteristicsScreen";

	public static ListView mListView = null;

	public static final String WRITE_WITH_RESPONSE = "With Response";

	public static final String WRITE_WITHOUT_RESPONSE = "Without Response";

	public static final String[] WriteDescription = {
                                          WRITE_WITH_RESPONSE,       // write with response
			                  WRITE_WITHOUT_RESPONSE };  // write without response

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.characteristicscreen);
		displayCharValue();
		displayCharCliConf();
	}

	private void displayCharValue() {

		BluetoothGattService gattService = getSelectedGattService();

		Log.d(TAG, "Gatt Service : " + gattService);

		TextView tv = (TextView) findViewById(R.id.textValueTitle);
		tv.setText("Characteristic Value ");

		Log.d(TAG, " Trying to read Char Raw on "
				+ mainScreen.selectedLEDevice.SelectedCharObjPath);
		byte[] CharReadRawVal = gattService
				.readCharacteristicRaw(mainScreen.selectedLEDevice.SelectedCharObjPath);

		if (CharReadRawVal != null) {
			Log.d(TAG, " CHAR READ RAW LENGTH ==== " + CharReadRawVal.length);
			for (int i = 0; i < CharReadRawVal.length; i++) {
				Log.d(TAG, " CHAR READ RAW ==== " + CharReadRawVal[i]);
			}

			String CharVal = new String(CharReadRawVal);
			Log.d(TAG, " CHAR READ RAW Convert to String ==== " + CharVal);

			TextView tv2 = (TextView) findViewById(R.id.textValue);
			tv2.setText(CharVal);
			EditText edText = (EditText) findViewById(R.id.editValue);
			edText.setText(CharVal);
		} else {
			Log.d(TAG, " ERROR CHAR READ RAW ==== returns NULL");

			TextView tv2 = (TextView) findViewById(R.id.textValue);
			tv2.setText("null");
			EditText edText = (EditText) findViewById(R.id.editValue);
			edText.setText("");

		}

		mListView = (ListView) findViewById(R.id.resposeList);

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_multiple_choice);

		for (int i = 0; i < WriteDescription.length; i++) {
			adapter.add(WriteDescription[i]);
		}

		mListView.setAdapter(adapter);
		mListView.setItemsCanFocus(false);
		mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		mListView.setItemChecked(0, true);

		Button button = (Button) findViewById(R.id.buttonSetCharValue);
		button.setText("Set Value");

		button.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				// TODO Auto-generated method stub
				EditText ed = (EditText) findViewById(R.id.editValue);
				Boolean isResponseReq;
				if (ed.getText() != null) {
					String editVal = ed.getText().toString();
					Log.d(TAG, "Edited Val = " + editVal);
					if (editVal.length() % 2 != 0) {
						ed.setTextColor(Color.rgb(255, 0, 0));
					} else {
						ed.setTextColor(Color.rgb(0, 0, 255));
						try {
							int checkedItemPos = mListView
									.getCheckedItemPosition();
							Log.d(TAG,
									" Position of CHECKED ITEM for WRITE======== "
									+ checkedItemPos);
							String writeOption = WriteDescription[checkedItemPos];
							if (writeOption.equals(WRITE_WITH_RESPONSE)) {
								Log.d(TAG, "Write option is :"
										+ WRITE_WITH_RESPONSE);
								isResponseReq = true;
							} else {
								Log.d(TAG, "Write option is :"
										+ WRITE_WITHOUT_RESPONSE);
								isResponseReq = false;
							}
							Log.d(TAG, "writeCharacteristicRaw response value :" + isResponseReq);

							getSelectedGattService()
									.writeCharacteristicRaw(
											mainScreen.selectedLEDevice.SelectedCharObjPath,
											hexStringToByteArray(editVal),
											isResponseReq);
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}

			private byte[] hexStringToByteArray(String s) {
				int len = s.length();
				byte[] data = new byte[len / 2];
				for (int i = 0; i < len; i += 2) {
					data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character
							.digit(s.charAt(i + 1), 16));
				}
				return data;
			}

		});

		Button buttonUpdateCharValue = (Button) findViewById(R.id.buttonUpdateCharValue);
		buttonUpdateCharValue.setText("Update Char Value");

		buttonUpdateCharValue.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				// TODO Auto-generated method stub
				String charPath = mainScreen.selectedLEDevice.SelectedCharObjPath;
				boolean updateOperation = false;
				try {
					updateOperation = getSelectedGattService()
							.updateCharacteristicValue(charPath);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Log.d(TAG, "updateValue return " + updateOperation);
			}
		});

	}

	private BluetoothGattService getSelectedGattService() {
		BluetoothGattService gattService = mainScreen.selectedLEDevice.objPathGattSrvMap
				.get(mainScreen.selectedLEDevice.SelectedServiceObjPath);
		return gattService;
	}

	private void displayCharCliConf() {

		BluetoothGattService gattService = getSelectedGattService();
		TextView tv = (TextView) findViewById(R.id.textCliConfTitle);
		tv.setText("Characteristic Cli Conf ");

		Log.d(TAG, " Trying to read Cli Conf on "
				+ mainScreen.selectedLEDevice.SelectedCharObjPath);

		String CharCliConf = gattService
				.getCharacteristicClientConf(mainScreen.selectedLEDevice.SelectedCharObjPath);

		if (CharCliConf != null) {

			Log.d(TAG, " CHAR CLI CONF " + CharCliConf);

			TextView tv2 = (TextView) findViewById(R.id.textCliConf);
			tv2.setText(CharCliConf);
			EditText edText = (EditText) findViewById(R.id.editCliConf);
			edText.setText(CharCliConf);

		} else {
			Log.d(TAG, " ERROR CHAR CLI CONF returns NULL");

			TextView tv2 = (TextView) findViewById(R.id.textCliConf);
			tv2.setText("null");
			EditText edText = (EditText) findViewById(R.id.editCliConf);
			edText.setText("");

		}

		Button button = (Button) findViewById(R.id.buttonUpdateCliConf);
		button.setText("Update Value");

		button.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				// TODO Auto-generated method stub
				EditText ed = (EditText) findViewById(R.id.editCliConf);
				if (ed.getText() != null) {
					String editCliConf = ed.getText().toString();
					Log.d(TAG, "Edited Val = " + editCliConf);
					ed.setTextColor(Color.rgb(0, 0, 255));
					try {
						getSelectedGattService()
								.setCharacteristicClientConf(
										mainScreen.selectedLEDevice.SelectedCharObjPath,
										Integer.valueOf(editCliConf));
					} catch (NumberFormatException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		});

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.optionsmenu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.enableWatcher:
			mainScreen.selectedLEDevice.registerWatcher();
			return true;
		case R.id.disableWatcher:
			mainScreen.selectedLEDevice.deregisterWatcher();
			return true;
		case R.id.NotificationIndication:
			Intent in = new Intent();
			in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			in.setClass(mainScreen.mainContext,
					com.android.le.GattClientTestApp.NotificationIndicationScreen.class);
			getApplicationContext().startActivity(in);
			return true;
		case R.id.ClearNotificationIndication:
			mainScreen.selectedLEDevice.clearNotificationIndication();
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

}
