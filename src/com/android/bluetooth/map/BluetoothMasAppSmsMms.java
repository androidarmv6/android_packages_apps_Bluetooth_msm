/*
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *        * Redistributions of source code must retain the above copyright
 *          notice, this list of conditions and the following disclaimer.
 *        * Redistributions in binary form must reproduce the above copyright
 *          notice, this list of conditions and the following disclaimer in the
 *          documentation and/or other materials provided with the distribution.
 *        * Neither the name of Code Aurora nor
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
package com.android.bluetooth.map;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import com.android.bluetooth.map.MapUtils.SmsMmsUtils;

import java.util.List;

/**
 * This class provides the application interface for MAS Server It interacts
 * with the SMS repository using Sms Content Provider to service the MAS
 * requests. It also initializes BluetoothMns thread which is used for MNS
 * connection.
 */

public class BluetoothMasAppSmsMms extends BluetoothMasAppIf {
    public final String TAG = "BluetoothMasAppSmsMms";
    public BluetoothMasAppSmsMms(Context context, Handler handler, BluetoothMns mnsClient,
            int masId) {
        super(context, handler, MESSAGE_TYPE_SMS_MMS, masId);
        this.mnsClient = mnsClient;

        // Clear out deleted items from database
        cleanUp();

        if (V) Log.v(TAG, "BluetoothMasAppSmsMms Constructor called");
    }

    /**
     * Start an MNS obex client session and push notification whenever available
     */
    public void startMnsSession(BluetoothDevice remoteDevice) {
        if (V) Log.v(TAG, "Start MNS Client");
        mnsClient.getHandler()
                .obtainMessage(BluetoothMns.MNS_CONNECT, 0, -1, remoteDevice)
                .sendToTarget();
    }

    /**
     * Stop pushing notifications and disconnect MNS obex session
     */
    public void stopMnsSession(BluetoothDevice remoteDevice) {
        if (V) Log.v(TAG, "Stop MNS Client");
        mnsClient.getHandler()
                .obtainMessage(BluetoothMns.MNS_DISCONNECT, 0, -1,
                remoteDevice).sendToTarget();
    }

    @Override
    protected List<String> getCompleteFolderList() {
        return SmsMmsUtils.FORLDER_LIST_SMS_MMS;
    }

    private void cleanUp() {
        // Remove the deleted item entries
        mContext.getContentResolver().delete(Uri.parse("content://sms/"),
                "thread_id = " + DELETED_THREAD_ID, null);
        mContext.getContentResolver().delete(Uri.parse("content://mms/"),
                "thread_id = " + DELETED_THREAD_ID, null);
    }

    public boolean checkPrecondition() {
        // TODO: Add any precondition check routine for this MAS instance
        return true;
    }

    public void onConnect() {
        // TODO: Add any routine to be run when OBEX connection established
    }

    public void onDisconnect() {
        cleanUp();
    }
}
