package com.qualcomm.qti.networksetting;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.app.Activity;
import android.content.Intent;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.WindowManager;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;


import com.qualcomm.qti.networksetting.INetworkQueryService;
import com.qualcomm.qti.networksetting.INetworkQueryServiceCallback;
import com.qualcomm.qti.networksetting.NetworkQueryService;
import com.qualcomm.qti.networksetting.NetworkSettingDataManager;


public class AutoStartBroadcastReceiver extends BroadcastReceiver {

	public static final String TAG = "AutoStartBroadcastReceiver";
	private static final String ACTION = "android.intent.action.BOOT_COMPLETED";

	@Override
	public void onReceive(Context context, Intent intent) {

		Log.d(TAG, "AutoStartBroadcastReceiver onReceive");
		if (intent.getAction().equals(ACTION)) {
			Log.d(TAG, "receive BOOT_COMPLETED, start NetworkSetting");

			//Log.d(TAG, "receive BOOT_COMPLETED, start DataControlService");
			//Intent intent_start_service = new Intent(context,NetworkSettingService.class);
			//context.startService(intent_start_service);

			Intent intent1 = new Intent(context, NetworkSettingDialog.class);
			intent1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK );
			context.startActivity(intent1);
		}
	}
}

