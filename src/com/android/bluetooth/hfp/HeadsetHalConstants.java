/*
 * Copyright (C) 2012 The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.bluetooth.hfp;

/*
 * @hide
 */

final public class HeadsetHalConstants {
    /*CIEV Indiacators for the call states*/
    /* Service:{0,1}*/
    final static int NETWORK_STATE_NOT_AVAILABLE = 0;
    final static int NETWORK_STATE_AVAILABLE = 1;
    /*Roam:{0,1}*/
    final static int SERVICE_TYPE_HOME = 0;
    final static int SERVICE_TYPE_ROAMING = 1;
    /*Call:{0,1}*/
    final static int CALL_CIEV_INACTIVE = 0;
    final static int CALL_CIEV_ACTIVE = 1;
    /*CallSetup:{0,1,2,3}*/
    final static int CALLSETUP_CIEV_IDLE = 0;
    final static int CALLSETUP_CIEV_INCOMING = 1;
    final static int CALLSETUP_CIEV_OUTGOING = 2;
    final static int CALLSETUP_CIEV_OUTGOING_ALERT = 3;
    /*Callheld:{0,1,2}*/
    final static int CALL_CIEV_NOHELD = 0;
    final static int CALL_CIEV_ACTIVE_AND_HELD = 1;
    final static int CALL_CIEV_HELD_NOACTIVE = 2;
    /*Call States.Mapping from BluetoothPhoneService*/
    final static int CALL_STATE_ACTIVE = 0;
    final static int CALL_STATE_HELD = 1;
    final static int CALL_STATE_DIALING = 2;
    final static int CALL_STATE_ALERTING = 3;
    final static int CALL_STATE_INCOMING = 4;
    final static int CALL_STATE_WAITING = 5;
    final static int CALL_STATE_IDLE = 6;
}
