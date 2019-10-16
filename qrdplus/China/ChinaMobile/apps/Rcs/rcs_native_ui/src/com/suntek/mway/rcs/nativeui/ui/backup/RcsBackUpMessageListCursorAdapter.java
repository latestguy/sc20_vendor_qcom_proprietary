/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */
package com.suntek.mway.rcs.nativeui.ui.backup;

import android.content.Context;
import android.database.Cursor;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.suntek.mway.rcs.nativeui.R;

import java.sql.Date;

public class RcsBackUpMessageListCursorAdapter extends CursorAdapter implements ListAdapter {

    private Cursor mCursor;

    private Context mContext;

    private LayoutInflater mInflater;

    private int mSendMessage = 1;

    public RcsBackUpMessageListCursorAdapter(Context context, Cursor c) {
        super(context, c);
        this.mCursor = c;
        this.mContext = context;
        this.mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        // TODO Auto-generated method stub
        View view = mInflater.inflate(R.layout.backup_message_item, parent, false);
        ViewHolder viewHolder = new ViewHolder(view);
        view.setTag(viewHolder);
        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {

        ViewHolder viewHolder = (ViewHolder)view.getTag();

        String number = cursor.getString(cursor.getColumnIndex("address"));
        String content = cursor.getString(cursor.getColumnIndex("body"));
        int subId = cursor.getInt(cursor.getColumnIndex("sub_id"));
        int direction = cursor.getInt(cursor.getColumnIndex("type"));
        long time = cursor.getLong(cursor.getColumnIndex("date"));
        int msgType = cursor.getInt(cursor.getColumnIndex("rcs_msg_type"));
        viewHolder.numberView.setText(number);

        viewHolder.contentView.setText(content);

        viewHolder.directionView.setText(direction == mSendMessage ? R.string.send_message
                : R.string.receive_message);
        viewHolder.timeView.setText(new Date(time) + "");

        SubscriptionInfo sir = SubscriptionManager.from(mContext).getActiveSubscriptionInfo(subId);
        String displayName = (sir != null) ? sir.getDisplayName().toString() : "";

        viewHolder.cardView.setText(displayName);
    }

    class ViewHolder {
        TextView numberView, contentView, cardView, directionView, timeView;

        public ViewHolder(View view) {
            numberView = (TextView)view.findViewById(R.id.message_number);
            contentView = (TextView)view.findViewById(R.id.message_content);
            cardView = (TextView)view.findViewById(R.id.message_card);
            directionView = (TextView)view.findViewById(R.id.message_direction);
            timeView = (TextView)view.findViewById(R.id.message_time);
        }
    }
}
