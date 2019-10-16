/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.ui;

import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.suntek.mway.rcs.nativeui.R;
import com.suntek.mway.rcs.nativeui.utils.RcsContactUtils;
import com.suntek.mway.rcs.client.aidl.common.RcsColumns;
import com.suntek.mway.rcs.client.aidl.constant.Constants.MessageConstants;

public class OneToManyStatusActivity extends Activity {

    private static final String EXTRA_VIEW_ONE_TO_MANY_MSG_STATUS_MSG_ID = "view_id";
    private static final String EXTRA_VIEW_ONE_TO_MANY_MSG_STATUS_MSG_BODY = "view_body";
    // message status
    public static final int MESSAGE_SENDING = MessageConstants.CONST_STATUS_SENDING;

    public static final int MESSAGE_HAS_SENDED = MessageConstants.CONST_STATUS_SENDED;

    public static final int MESSAGE_SENDED = MessageConstants.CONST_STATUS_SEND_RECEIVED;

    public static final int MESSAGE_FAIL = MessageConstants.CONST_STATUS_SEND_FAIL;

    public static final int MESSAGE_HAS_BURNED = MessageConstants.CONST_STATUS_BURNED;
    //delivered
    public static final int MESSAGE_SEND_RECEIVE = MessageConstants.CONST_STATUS_SEND_RECEIVED;
    //displayed
    public static final int MESSAGE_HAS_READ = MessageConstants.CONST_STATUS_ALREADY_READ;

    private static final Uri ONE_TO_MANY_STATUS_CONTENT_URI = Uri.parse(
            "content://mms-sms/oneToManyStatus");

    private static final int QUERY_STATUS = 455;
    private ListView mListView = null;
    private BackgroundQueryHandler mBackgroundQueryHandler;
    private oneToManyStatusListAdapter mAdapter;
    private long mMsgId;
    private String mBody;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rcs_one_to_many_status_activity);
        initView();
    }


    private void initView() {
        mListView = (ListView)findViewById(R.id.one_to_many_status_list);
        TextView emptyView = (TextView)findViewById(R.id.empty);
        mListView.setEmptyView(emptyView);
        Intent intent = getIntent();
        mMsgId = intent.getLongExtra(EXTRA_VIEW_ONE_TO_MANY_MSG_STATUS_MSG_ID, -1);
        mBody = intent.getStringExtra(EXTRA_VIEW_ONE_TO_MANY_MSG_STATUS_MSG_BODY);
        mBackgroundQueryHandler = new BackgroundQueryHandler(getContentResolver());
        mBackgroundQueryHandler.startQuery(QUERY_STATUS, null, ONE_TO_MANY_STATUS_CONTENT_URI,
                null, RcsColumns.GroupStatusColumns.MSG_ID + " = " + mMsgId, null, null);
    }




    public class oneToManyStatusListAdapter extends CursorAdapter {
        private LayoutInflater mInflater;

        @SuppressWarnings("deprecation")
        public oneToManyStatusListAdapter(Context context, Cursor cursor) {
            super(context, cursor);
            mInflater = (LayoutInflater) context.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            viewHolder holder = null;
            if(view.getTag() == null) {
                holder = new viewHolder(view);
                view.setTag(holder);
            } else {
                holder = (viewHolder) view.getTag();
            }
            String number = cursor.getString(cursor
                    .getColumnIndex(RcsColumns.GroupStatusColumns.GROUP_NUMBER));
            String name = RcsContactUtils.getContactNameFromPhoneBook(
                    OneToManyStatusActivity.this, number, null);
            holder.mMenber.setText(name);
            int status = cursor.getInt(cursor
                    .getColumnIndex(RcsColumns.GroupStatusColumns.GROUP_STATUS));
            holder.mStatus.setText(getStatusString(status));

        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return  mInflater.inflate(R.layout.rcs_one_to_many_status_list_item, parent, false);
        }

        private int getStatusString(int status) {
             int statusString;
                switch(status) {
                    case MESSAGE_HAS_SENDED:
                        statusString = R.string.message_status_sent;
                        break;
                    case MESSAGE_SENDED:
                        statusString = R.string.message_status_recieved;
                        break;
                    case MESSAGE_HAS_READ:
                        statusString = R.string.message_status_readed;
                        break;
                    case MESSAGE_FAIL:
                        statusString = R.string.message_status_fail;
                        break;
                    default:
                        statusString = R.string.message_status_sent;
                        break;
                }
            return statusString;
        }
    }

    private class viewHolder {
        TextView mMenber;
        TextView mStatus;
        ImageView mStatusImage;

        public viewHolder(View convertView) {
            this.mMenber = (TextView)convertView.findViewById(R.id.menber);
            this.mStatus = (TextView)convertView.findViewById(R.id.msg_status);
            this.mStatusImage = (ImageView)convertView.findViewById(R.id.status_image);
        }
    }

     private class BackgroundQueryHandler extends AsyncQueryHandler {
            public BackgroundQueryHandler(ContentResolver contentResolver) {
                super(contentResolver);
            }

            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                if (QUERY_STATUS == token && cursor != null) {
                    mAdapter = new oneToManyStatusListAdapter(OneToManyStatusActivity.this, cursor);
                    mListView.setAdapter(mAdapter);
                    mListView.setOnItemClickListener(new OnItemClickListener() {

                        @Override
                        public void onItemClick(AdapterView<?> arg0, View arg1,
                                int arg2, long arg3) {

                        }
                    });
                }
            }

     }
}
