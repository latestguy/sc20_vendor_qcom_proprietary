/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.nativeui.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import com.suntek.mway.rcs.nativeui.R;
import com.suntek.mway.rcs.nativeui.utils.RcsContactUtils;
import com.suntek.rcs.ui.common.PropertyNode;
import com.suntek.rcs.ui.common.VNode;

public class RcsGroupVcardDetailActivity extends Activity {

    private ListView mListView;
    private String mFilePath;
    private List<VNode> mVnodeList;
    private RcsGroupVcardAdapter mAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rcs_group_vcard_detail_activity);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        mFilePath = getIntent().getStringExtra("vcardFilePath");
        if (TextUtils.isEmpty(mFilePath)) {
            finish();
            return;
        }
        initView();
    }

    private void initView() {
        mListView = (ListView) findViewById(R.id.list_view);
        mVnodeList = RcsContactUtils.rcsVcardContactList(this, mFilePath);
        mAdapter = new RcsGroupVcardAdapter(this, mVnodeList);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position,
                    long arg3) {
                ArrayList<PropertyNode> propList = mVnodeList.get(position).propList;
                RcsContactUtils.showDetailVcard(RcsGroupVcardDetailActivity.this, propList);
            }
        });
    }

    class RcsGroupVcardAdapter extends BaseAdapter {

        private Context mContext;
        private List<VNode> mContactList;
        private LayoutInflater mLayoutInflater;

        public RcsGroupVcardAdapter (Context context, List<VNode> vNode) {
            this.mContext = context;
            this.mContactList = vNode;
            this.mLayoutInflater = LayoutInflater.from(context);
        }

        class ViewHolder {
            ImageView mPhoto;
            TextView mName;
            TextView mPhone;
            TextView mCompany;
            TextView mPosition;
        }

        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return mContactList.size();
        }

        @Override
        public Object getItem(int arg0) {
            // TODO Auto-generated method stub
            return mContactList.get(arg0).propList;
        }

        @Override
        public long getItemId(int arg0) {
            // TODO Auto-generated method stub
            return arg0;
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            ViewHolder holder;
            if (view == null) {
                view = mLayoutInflater.inflate(R.layout.rcs_group_vcard_item, null);
                holder = new ViewHolder();
                holder.mPhoto = (ImageView) view.findViewById(R.id.photo);
                holder.mName = (TextView) view.findViewById(R.id.name);
                holder.mPhone = (TextView) view.findViewById(R.id.phone_number);
                holder.mCompany = (TextView) view.findViewById(R.id.company);
                holder.mPosition = (TextView) view.findViewById(R.id.position);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }
            if (mContactList !=null && mContactList.size() > 0) {
                Bitmap bitmap = null;
                String name = "";
                String number = "";
                String phoneNumber = null;
                String homeNumber = null;
                String workNumber = null;
                String company = "";
                String posText = "";
                ArrayList<PropertyNode> propList = mContactList.get(position).propList;
                if (propList != null) {
                    for (PropertyNode propertyNode : propList) {
                        if ("PHOTO".equals(propertyNode.propName)) {
                            if (propertyNode.propValue_bytes != null) {
                                byte[] bytes = propertyNode.propValue_bytes;
                                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                bitmap = RcsContactUtils.decodeInSampleSizeBitmap(bitmap);
                            }
                        } else if ("FN".equals(propertyNode.propName)) {
                            if(!TextUtils.isEmpty(propertyNode.propValue)){
                                name = propertyNode.propValue;
                            }
                        } else if ("TEL".equals(propertyNode.propName)) {
                            if(!TextUtils.isEmpty(propertyNode.propValue)){
                                if (propertyNode.paramMap_TYPE.contains("CELL")
                                        && !propertyNode.paramMap_TYPE.contains("WORK")) {
                                    phoneNumber = propertyNode.propValue;
                                } else if (propertyNode.paramMap_TYPE.contains("HOME")
                                        && !propertyNode.paramMap_TYPE.contains("FAX")){
                                    homeNumber = propertyNode.propValue;
                                } else if(propertyNode.paramMap_TYPE.contains("WORK")
                                        && !propertyNode.paramMap_TYPE.contains("FAX")){
                                    workNumber = propertyNode.propValue;
                                } else {
                                    number = RcsContactUtils.getPhoneNumberTypeStr(mContext,
                                            propertyNode);
                                }
                            }
                        } else if ("ORG".equals(propertyNode.propName)) {
                            if (!TextUtils.isEmpty(propertyNode.propValue)) {
                                company = propertyNode.propValue;
                            }
                        }  else if ("TITLE".equals(propertyNode.propName)) {
                            if (!TextUtils.isEmpty(propertyNode.propValue)) {
                                posText = propertyNode.propValue;
                            }
                        }
                    }
                    if (!TextUtils.isEmpty(phoneNumber)) {
                        number = phoneNumber;
                    } else if (!TextUtils.isEmpty(homeNumber)) {
                        number = homeNumber;
                    } else if (!TextUtils.isEmpty(workNumber)) {
                        number = workNumber;
                    }
                    if (bitmap == null) {
                        bitmap = BitmapFactory.decodeResource(mContext.getResources(),
                                R.drawable.rcs_group_vcard_item);
                        holder.mPhoto.setImageBitmap(bitmap);
                    } else {
                        bitmap = RcsContactUtils.toCornerRadius(bitmap, 180);
                        holder.mPhoto.setImageBitmap(bitmap);
                    }

                    holder.mName.setText(name);
                    holder.mPhone.setText(number);
                    holder.mCompany.setText(company);
                    holder.mPosition.setText(posText);
                }
            }
            return view;
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            break;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

}
