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
import android.database.ContentObserver;
import android.os.Handler;
import android.util.Log;

import com.android.bluetooth.map.MapUtils.EmailUtils;

import java.util.List;

import static com.android.bluetooth.map.BluetoothMasService.MSG_SERVERSESSION_CLOSE;
import static com.android.bluetooth.map.MapUtils.SmsMmsUtils.DELETED;
import static com.android.bluetooth.map.MapUtils.SmsMmsUtils.DRAFT;
import static com.android.bluetooth.map.MapUtils.SmsMmsUtils.INBOX;
import static com.android.bluetooth.map.MapUtils.SmsMmsUtils.OUTBOX;
import static com.android.bluetooth.map.MapUtils.SmsMmsUtils.SENT;

/**
 * This class provides the application interface for MAS Server It interacts
 * with the SMS repository using Sms Content Provider to service the MAS
 * requests. It also initializes BluetoothMns thread which is used for MNS
 * connection.
 */

public class BluetoothMasAppEmail extends BluetoothMasAppIf {
    public final String TAG = "BluetoothMasAppEmail";
    public final boolean V = BluetoothMasService.VERBOSE;;

    private ContentObserver mObserver;

    public BluetoothMasAppEmail(Context context, Handler handler, BluetoothMns mnsClient,
            int masId) {
        super(context, handler, MESSAGE_TYPE_EMAIL, masId);
        this.mnsClient = mnsClient;

        mObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                long id = EmailUtils.getDefaultEmailAccountId(mContext);
                if (id == -1) {
                    // no email account, disconnect
                    // TODO: inform the user
                    disconnect();
                }
                super.onChange(selfChange);
            }
        };

        if (V) Log.v(TAG, "BluetoothMasAppEmail Constructor called");
    }

    /**
     * Start an MNS obex client session and push notification whenever available
     */
    public void startMnsSession(BluetoothDevice remoteDevice) {
        if (V) Log.v(TAG, "Start MNS Client");
        mnsClient.getHandler().obtainMessage(BluetoothMns.MNS_CONNECT, 1,
                -1, remoteDevice).sendToTarget();
    }

    /**
     * Stop pushing notifications and disconnect MNS obex session
     */
    public void stopMnsSession(BluetoothDevice remoteDevice) {
        if (V) Log.v(TAG, "Stop MNS Client");
        mnsClient.getHandler().obtainMessage(BluetoothMns.MNS_DISCONNECT, 1,
                -1, remoteDevice).sendToTarget();
    }

    @Override
    protected List<String> getCompleteFolderList() {
        if (V) Log.v(TAG, "getCompleteFolderList");
        // TODO differentiate email account id, take default email account for now
        long id = EmailUtils.getDefaultEmailAccountId(mContext);
        List<String> list = EmailUtils.getEmailFolderList(mContext, id);
        if (!list.contains(INBOX)) {
            list.add(INBOX);
        }
        if (!list.contains(OUTBOX)) {
            list.add(OUTBOX);
        }
        if (!list.contains(SENT)) {
            list.add(SENT);
        }
        if (!list.contains(DELETED)) {
            list.add(DELETED);
        }
        if (!list.contains(DRAFT)) {
            list.add(DRAFT);
        }
        return list;
    }

    public boolean checkPrecondition() {
        long id = EmailUtils.getDefaultEmailAccountId(mContext);
        if (id == -1) {
            // no email account found
            if (V) Log.v(TAG, "No Email account found.");
            return false;
        } else if (V) {
            if (V) Log.v(TAG, "Email account found.");
        }
        return true;
    }

    public void onConnect() {
        if (V) Log.v(TAG, "onConnect() registering email account content observer");
        mContext.getContentResolver().registerContentObserver(
                EmailUtils.EMAIL_ACCOUNT_URI, true, mObserver);
    }

    public void onDisconnect() {
        if (V) Log.v(TAG, "onDisconnect() unregistering email account content observer");
        mContext.getContentResolver().unregisterContentObserver(mObserver);
    }

    private void disconnect() {
        if (V) Log.v(TAG, "disconnect() sending serversession close.");
        mHandler.obtainMessage(MSG_SERVERSESSION_CLOSE, mMasId, -1).sendToTarget();
    }
}
