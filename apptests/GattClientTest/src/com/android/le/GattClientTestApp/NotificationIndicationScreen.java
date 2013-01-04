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
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class NotificationIndicationScreen extends Activity {

	public final String TAG = "NotificationIndicationScreen";

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Log.d(TAG, "Set Layout ");
            setContentView(R.layout.listofconnecteddevices);

            Log.d(TAG, "Getting Text View");

            TextView tv = (TextView) findViewById(R.id.textViewHeader);
            tv.setText("NOTIFICATIONS / INDICATIONS RECVD");

            Log.d(TAG, "Setting List View");

            ListView mListView = (ListView) findViewById(R.id.RegularList);

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);

            Log.d(TAG, "Lets add ObjPath");
            if ( mainScreen.selectedLEDevice.NotificationIndications != null) {
            for (int i = 0; i < mainScreen.selectedLEDevice.NotificationIndications.size(); i++) {
            	Log.d(TAG, "ADD " + i);
            	Log.d(TAG, "Adding ObjPath" + mainScreen.selectedLEDevice.NotificationIndications.get(i).toString());
            	adapter.add(mainScreen.selectedLEDevice.NotificationIndications.get(i).toString());
            }
            }

            mListView.setAdapter(adapter);
            mListView.setItemsCanFocus(false);
            mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            mListView.setItemChecked(0, true);
    }
    /*@Override
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
        	// Do nothing
            return true;
        case R.id.ClearNotificationIndication:
        	mainScreen.selectedLEDevice.clearNotificationIndication();
            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }*/
}
