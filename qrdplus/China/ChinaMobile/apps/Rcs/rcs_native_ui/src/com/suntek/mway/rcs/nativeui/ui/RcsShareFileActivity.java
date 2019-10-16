/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */
package com.suntek.mway.rcs.nativeui.ui;



import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcelable;
import android.os.RemoteException;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

import java.util.ArrayList;
import java.util.Arrays;

import com.suntek.mway.rcs.client.aidl.constant.Actions;
import com.suntek.mway.rcs.client.aidl.constant.Constants;
import com.suntek.mway.rcs.client.aidl.constant.Constants.PluginConstants;
import com.suntek.mway.rcs.client.aidl.constant.Parameter;
import com.suntek.mway.rcs.client.aidl.plugin.entity.cloudfile.FileNode;
import com.suntek.mway.rcs.client.aidl.plugin.callback.ICloudOperationCtrl;
import com.suntek.mway.rcs.client.aidl.plugin.entity.cloudfile.FileNode;
import com.suntek.mway.rcs.client.aidl.plugin.entity.cloudfile.TransNode.TransOper;
import com.suntek.mway.rcs.client.api.cloudfile.CloudFileApi;
import com.suntek.mway.rcs.client.api.exception.FileSuffixException;
import com.suntek.mway.rcs.client.api.exception.ServiceDisconnectedException;
import com.suntek.mway.rcs.nativeui.ui.SaiunFileShareAdapter.ViewHolder;
import com.suntek.mway.rcs.nativeui.utils.ImageUtils;
import com.suntek.mway.rcs.nativeui.R;
import com.suntek.rcs.ui.common.RcsLog;

public class RcsShareFileActivity extends Activity implements OnClickListener {

    private static final int REQUEST_SELECT_SAIUN_LOCAL_FILE = 11;

    private ArrayList<FileNode> mShareNodeList = new ArrayList<FileNode>();
    private ListView mListView;
    private Context mContext;
    private SaiunFileShareAdapter mSaiunFileShareAdapter;
    private static ICloudOperationCtrl mOpreration = null;
    private CloudFileApi mCloudFileApi = null;
    private ProgressDialog mDialog;
    private ProgressDialog mProgressDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = RcsShareFileActivity.this;
        setContentView(R.layout.rcs_share_file_activity);
        register_Receiver();
        initView();
        getRemoteFileList("", -1, 0);
        showWaitDialog(mContext);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void showWaitDialog(Context context){
        mDialog = ProgressDialog.show(
                context,
                context.getString(R.string.please_wait_moment),
                context.getString(R.string.caiyun_file_is_downing),
                false);
        mDialog.setCanceledOnTouchOutside(true);
    }

    private void getRemoteFileList(String remotePath, int beginIndex, int endIndex) {
        try {
            CloudFileApi.getInstance()
                    .getRemoteFileList(remotePath, beginIndex, endIndex,
                     FileNode.Order.createdate);
        } catch (ServiceDisconnectedException e) {
            RcsLog.i("get remote file list error");
            dissmissDialog();
            showSelectContinueOrReturn();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void initView() {
        mListView = (ListView) findViewById(R.id.saiun_file_list);
        mSaiunFileShareAdapter = new SaiunFileShareAdapter(this);
        mListView.setAdapter(mSaiunFileShareAdapter);
        mSaiunFileShareAdapter.setDatas(mShareNodeList);
        mListView.setOnItemClickListener(mOnItemClickListener);
    }

    private OnItemClickListener mOnItemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {
            ViewHolder mViewHolder = (ViewHolder) view.getTag();
            FileNode mFileNode = (FileNode) mViewHolder.mFileName.getTag();
            // TODO check if is file
            if (mFileNode.isFile()) {
                Intent intent = new Intent();
                intent.putExtra("id", mFileNode.getId());
                setResult(RESULT_OK, intent);
                finish();
            } else {
                getRemoteFileList(mFileNode.getId(), -1, 0);
            }
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.back_layout:
            finish();
            break;
        case R.id.btn_upload_saiun_file:
            showUploadFileDialog();
            break;
        }
    }

    private void showUploadFileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                RcsShareFileActivity.this);
        builder.setTitle(R.string.rcs_saiun_file_upload_file);
        builder.setMessage(mContext
                .getString(R.string.rcs_saiun_file_upload_marked_words));
        builder.setPositiveButton(R.string.rcs_confirm,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("*/*");
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        startActivityForResult(intent,
                                REQUEST_SELECT_SAIUN_LOCAL_FILE);
                    }
                });
        builder.setNegativeButton(R.string.rcs_cancel, null);
        builder.create().show();
    }

    private void showSelectContinueOrReturn() {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                RcsShareFileActivity.this);
        builder.setTitle(R.string.rcs_storage_manager_tip);
        builder.setMessage(R.string.please_select_continue_or_return);
        builder.setPositiveButton(R.string.rcs_confirm,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                      getRemoteFileList("", -1, 0);
                      mDialog.show();
                    }
                });
        builder.setNegativeButton(R.string.rcs_cancel,new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              finish();
            }
        });
        builder.create().show();
    }

    private void register_Receiver() {
        RcsLog.i("register receiver");
        IntentFilter contactfilter = new IntentFilter(
                Actions.PluginAction.ACTION_MCLOUD_GET_SHARE_FILE_LIST);
        contactfilter.addAction(Actions.PluginAction.ACTION_MCLOUD_PUT_FILE);
        contactfilter.addAction(Actions.PluginAction.ACTION_MCLOUD_GET_REMOTE_FILE_LIST);

        this.registerReceiver(receiver, contactfilter);
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String eventType = intent.getStringExtra(Parameter.EXTRA_MCLOUD_ENENTTYPE);
            RcsLog.i("action="+action + " eventType = "+ eventType);
            if (action.equals(Actions.PluginAction.ACTION_MCLOUD_GET_SHARE_FILE_LIST)) {
                if (PluginConstants.CONST_MCLOUD_EVENT_SUCCESS.equals(eventType)) {
                    Parcelable[] fileNodeList = (Parcelable[]) intent
                            .getParcelableArrayExtra(Parameter.EXTRA_MCLOUD_SHARE_NODE_LIST);
                    FileNode[] resultArray = null;

                    if (fileNodeList != null) {
                        resultArray = Arrays.copyOf(fileNodeList, fileNodeList.length,
                                FileNode[].class);
                    }
                    mShareNodeList.clear();
                    mShareNodeList.addAll(Arrays.asList(resultArray));

                    if (mSaiunFileShareAdapter != null)
                        mSaiunFileShareAdapter.setDatas(mShareNodeList);
                } else if (PluginConstants.CONST_MCLOUD_EVENT_ERROR.equals(eventType)) {
                    String error = intent.getStringExtra(Parameter.EXTRA_MCLOUD_MESSAGE);
                    Toast.makeText(RcsShareFileActivity.this,
                            R.string.rcs_get_saiun_file_list_fail,
                            Toast.LENGTH_SHORT).show();
                }
            } else if (action.equals(Actions.PluginAction.ACTION_MCLOUD_GET_REMOTE_FILE_LIST)) {
                if (PluginConstants.CONST_MCLOUD_EVENT_SUCCESS.equals(eventType)) {
                    Parcelable[] fileNodeList = (Parcelable[]) intent
                            .getParcelableArrayExtra(Parameter.EXTRA_MCLOUD_REMOTE_NODE_LIST);
                    FileNode[] resultArray = null;
                    if (fileNodeList != null) {
                        resultArray = Arrays.copyOf(fileNodeList, fileNodeList.length,
                                FileNode[].class);
                    }
                    mShareNodeList.clear();
                    mShareNodeList.addAll(Arrays.asList(resultArray));
                    if (mSaiunFileShareAdapter != null)
                        mSaiunFileShareAdapter.setDatas(mShareNodeList);
                    dissmissDialog();
                } else if (PluginConstants.CONST_MCLOUD_EVENT_ERROR.equals(eventType)) {
                    dissmissDialog();
                    showSelectContinueOrReturn();
                }
            } else if (action.equals(Actions.PluginAction.ACTION_MCLOUD_PUT_FILE)) {
                if (PluginConstants.CONST_MCLOUD_EVENT_ERROR.equals(eventType)) {
                    String message = intent.getStringExtra(Parameter.EXTRA_MCLOUD_MESSAGE);
                    dissmissProgressDialog();
                    Toast.makeText(RcsShareFileActivity.this,
                            R.string.rcs_upload_saiun_file_fail, Toast.LENGTH_SHORT).show();
                } else if (PluginConstants.CONST_MCLOUD_EVENT_PROGRESS.equals(eventType)) {
                    float progressSize = (int) intent.getLongExtra(
                            Parameter.EXTRA_MCLOUD_PROCESS_SIZE, 0);
                    float total = (int) intent.getLongExtra(Parameter.EXTRA_MCLOUD_TOTAL_SIZE, 0);
                    double percent = (double)(Math.round(progressSize/10)/100.0);
                    RcsLog.i("percent="+percent);
                    showProgressDialog(percent);
                } else if (PluginConstants.CONST_MCLOUD_EVENT_SUCCESS.equals(eventType)) {
                    dissmissProgressDialog();
                    String fullPathInID = intent.getStringExtra(Parameter.EXTRA_MCLOUD_FILE_ID);
                    RcsLog.i("fullPathInID="+fullPathInID);
                    getRemoteFileList("",-1, 0);
                    uploadSucess();
                } else if (eventType.equals(PluginConstants.CONST_MCLOUD_EVENT_FILE_TOO_LARGE)) {
                    long fileMaxSizeKb = intent.getLongExtra(Parameter.EXTRA_FILE_MAX_SIZE, 0);
                    String maxMB = ImageUtils.getFileSize(fileMaxSizeKb, 1024 * 1024, "MB");
                    dissmissProgressDialog();
                    Toast.makeText(RcsShareFileActivity.this, getBaseContext().getResources()
                            .getString(R.string.rcs_saiun_file_max_size_outstrip)
                            + maxMB, Toast.LENGTH_SHORT).show();
                } else if (eventType.equals(
                        PluginConstants.CONST_MCLOUD_EVENT_SUFFIX_NOT_ALLOWED)) {
                    String excludeFile = intent.getStringExtra(
                            Parameter.EXTRA_MCLOUD_EXCLUDE_SUFFIX);
                    dissmissProgressDialog();
                    String toastString = getBaseContext().getResources()
                            .getString(R.string.cloundfile_exclude) + excludeFile.toLowerCase();
                    Toast.makeText(RcsShareFileActivity.this, toastString,
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    private void showProgressDialog(double progress) {
        RcsLog.i("SHOW PROGRESSdIALOG");
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(RcsShareFileActivity.this);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setMessage(getBaseContext().getResources()
                    .getString(R.string.rcs_saiun_file_uploading_file));
            // mProgressDialog.setCancelable(false);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.setProgress((int)progress);
            mProgressDialog.setButton(mContext.getString(R.string.pause_upload),
                    new DialogInterface.OnClickListener() {
               @Override
                public void onClick(DialogInterface arg0, int arg1) {
                 cancelUpload();
               }
            });
            if (!mProgressDialog.isShowing()) {
                mProgressDialog.show();
            }
        } else {
            if (!mProgressDialog.isShowing()) {
                mProgressDialog.show();
            }
            mProgressDialog.setProgress((int)progress);
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_SELECT_SAIUN_LOCAL_FILE:
                    Uri uri3 = data.getData();
                    final String localPath = ImageUtils.getPath(this, uri3);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (ImageUtils.fileIsOutstrip(localPath, ImageUtils.ONE_MB, 500)) {
                                try {
                                    mOpreration = CloudFileApi.getInstance().putFile(localPath, "",
                                            TransOper.NEW);
                                } catch (RemoteException e) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(RcsShareFileActivity.this,
                                                    mContext.getString(
                                                    R.string.rcs_saiun_file_upload_legal_file),
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                } catch (ServiceDisconnectedException e) {
                                    e.printStackTrace();
                                }
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        showProgressDialog(0);
                                    }
                                });
                            } else {
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        Toast.makeText(RcsShareFileActivity.this,
                                                mContext.getString(
                                                R.string.rcs_saiun_file_upload_legal_file),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    }).start();
                    break;
            }
        }
    }

    private void cancelUpload() {
        if (mOpreration != null) {
            try {
                mOpreration.pause();
            } catch (Exception e) {
                e.printStackTrace();
            }
            Toast.makeText(RcsShareFileActivity.this,
                    mContext.getString(R.string.pause_uploaded),
                    Toast.LENGTH_SHORT).show();
            dissmissProgressDialog();
            return;
        }
        RcsLog.i("CANCEL UPLOAD BUT OPERATION == NULL");
    }

    private void uploadSucess() {
        mOpreration = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(receiver != null){
            unregisterReceiver(receiver);
            }
    }

    private void dissmissProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.cancel();
            mProgressDialog = null;
        }
    }

    private void dissmissDialog() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }
}
