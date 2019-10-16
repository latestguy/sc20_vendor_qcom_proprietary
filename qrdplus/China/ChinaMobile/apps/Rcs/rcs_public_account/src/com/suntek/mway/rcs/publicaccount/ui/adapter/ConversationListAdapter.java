/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.publicaccount.ui.adapter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccounts;
import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicAccountsDetail;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountCallback;
import com.suntek.mway.rcs.client.api.publicaccount.PublicAccountApi;
import com.suntek.mway.rcs.publicaccount.data.PublicConversation;
import com.suntek.mway.rcs.publicaccount.ui.PAMessageUtil;
import com.suntek.mway.rcs.publicaccount.util.AsynImageLoader;
import com.suntek.mway.rcs.publicaccount.util.AsynImageLoader.ImageCallback;
import com.suntek.mway.rcs.publicaccount.util.AsynImageLoader.LoaderImageTask;
import com.suntek.mway.rcs.publicaccount.R;
import com.suntek.rcs.ui.common.utils.ImageLoader;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class ConversationListAdapter extends BaseAdapter {

    class ViewHolder {
        ImageView imgBtn_Photo;

        TextView text_Name;

        TextView text_MessageCount;

        TextView text_LatestMessageType;

        TextView text_LatestMessageDate;

        RelativeLayout layout_photo;

        public ViewHolder(View convertView) {
            imgBtn_Photo = (ImageView)convertView.findViewById(R.id.imgBtn_Photo);
            text_Name = (TextView)convertView.findViewById(R.id.text_Name);
            text_MessageCount = (TextView)convertView.findViewById(R.id.textUnread);
            text_LatestMessageType = (TextView)convertView
                    .findViewById(R.id.text_LatestMessageType);
            text_LatestMessageDate = (TextView)convertView
                    .findViewById(R.id.text_LatestMessageDate);
            layout_photo = (RelativeLayout)convertView.findViewById(R.id.layout_photo);

            imgBtn_Photo.setFocusable(false);
        }
    }

    private Activity mContext;

    private LayoutInflater mInflater;

    private String[] today;

    private String numberToBeAdd = null;

    private ArrayList<PublicConversation> sessionList = new ArrayList<PublicConversation>();

    private ImageLoader mPublicImageLoader;

    public String[] getToday() {
        return today;
    }

    public void setToday(String[] today) {
        this.today = today;
        this.notifyDataSetChanged();
    }

    @SuppressLint("SimpleDateFormat")
    public ConversationListAdapter(Activity context, ImageLoader publicImageLoader) {
        mContext = context;
        mInflater = LayoutInflater.from(mContext);
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                context.getString(R.string.message_list_adapter_yyyy_mm_dd_));
        today = dateFormat.format(date).split(" ");
        mPublicImageLoader = publicImageLoader;
    }

    public void setDatas(ArrayList<PublicConversation> sessionList) {
        this.sessionList.clear();
        this.sessionList.addAll(sessionList);
        this.notifyDataSetChanged();
    }

    public String getNumberToBeAdd() {
        return numberToBeAdd;
    }

    @Override
    public int getCount() {
        return sessionList.size();
    }

    @Override
    public PublicConversation getItem(int arg0) {
        return sessionList.get(arg0);
    }

    @Override
    public long getItemId(int arg0) {
        return sessionList.get(arg0).getPublicAccountThreadId();
    }

    private void setUnreadCount(TextView textView, PublicConversation session) {
        try {
            int unreadCount = session.getUnRead();
            if (unreadCount > 0) {
                textView.setVisibility(View.VISIBLE);
                textView.setText(unreadCount + "");
            } else {
                textView.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setLastMsgStr(TextView textView, PublicConversation session) {

        String showMsg = PAMessageUtil.getLastMessageStr(mContext, session);

        textView.setText(showMsg);

    }

    @SuppressLint("SimpleDateFormat")
    private void setLastMsgDate(ViewHolder holder, PublicConversation session) {
        long time = session.getLastMessageTime();
        Date date = new Date(time);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                mContext.getString(R.string.message_list_adapter_yyyy_mm_dd_hh_mm));
        String sendTimeStr = dateFormat.format(date);
        String[] times = sendTimeStr.split(" ");
        if (!times[0].equals(today[0])) {
            holder.text_LatestMessageDate.setText(times[0]
                    + mContext.getString(R.string.message_list_adapter_year) + times[1]);
        } else if (!times[1].equals(today[1])) {
            holder.text_LatestMessageDate.setText(times[1]);
        } else {
            holder.text_LatestMessageDate.setText(times[2]);
        }
    }

    private void setNameAndLogo(final ViewHolder holder, PublicConversation session) {
        PublicAccounts publicAccounts = session.getPublicAccount();
        if (publicAccounts == null) {
            return;
        }
        String uuid = publicAccounts.getPaUuid();
        if (publicAccounts != null) {
            String name = publicAccounts.getName();
            holder.text_Name.setText(name);
            LoaderImageTask loaderImageTask = new LoaderImageTask(
                    publicAccounts.getLogo(), false, false, true, false);
            mPublicImageLoader.load(holder.imgBtn_Photo, publicAccounts.getLogo(),
                    R.drawable.rcs_public_account_btn_bg, R.drawable.rcs_public_account_btn_bg);
        }
    }

    @SuppressLint("SimpleDateFormat")
    @Override
    public View getView(int arg0, View convertView, ViewGroup arg2) {
        final ViewHolder holder;
        final PublicConversation session = getItem(arg0);
        if (convertView == null || convertView.getTag() == null) {
            convertView = mInflater.inflate(R.layout.conversation_list_item_view, null);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder)convertView.getTag();
        }
        setLastMsgDate(holder, session);
        setUnreadCount(holder.text_MessageCount, session);
        setLastMsgStr(holder.text_LatestMessageType, session);
        setNameAndLogo(holder, session);
        return convertView;
    }

}
