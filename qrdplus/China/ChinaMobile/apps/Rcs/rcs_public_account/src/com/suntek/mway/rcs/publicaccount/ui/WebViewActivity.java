/*
 * Copyright (c) 2014-2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.publicaccount.ui;

import java.util.ArrayList;
import java.util.Arrays;



import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.suntek.mway.rcs.client.aidl.plugin.entity.pubacct.PublicTopicMessage.PublicTopicContent;
import com.suntek.mway.rcs.publicaccount.R;
import com.suntek.mway.rcs.publicaccount.data.PublicMessageItem;

public class WebViewActivity extends Activity {

    private static final int MENU_FORWARD_MSG = 0;
    private static final int MENU_COPY_LINK = 1;
    private static final String MENU_TOPIC_TITLE = "title";
    private static final String MENU_TOPIC_URL = "url";
    private static final String TOPIC_CHATMESSAGE = "chatMessage";
    private static final String TOPIC_TOPICCONTENT = "topicContent";

    private PublicMessageItem mChatMessage;

    private PublicTopicContent mTopicContent;

    public static void start(Context context, String title, String url) {
        Intent intent = new Intent(context, WebViewActivity.class);
        intent.putExtra(MENU_TOPIC_TITLE, title);
        intent.putExtra(MENU_TOPIC_URL, url);
        context.startActivity(intent);
    }

    public static void start(Context context, String title, String url,
            PublicMessageItem chatMessage, PublicTopicContent topicContent) {
        Intent intent = new Intent(context, WebViewActivity.class);
        intent.putExtra(MENU_TOPIC_TITLE, title);
        intent.putExtra(MENU_TOPIC_URL, url);
        intent.putExtra(TOPIC_CHATMESSAGE, chatMessage);
        intent.putExtra(TOPIC_TOPICCONTENT, topicContent);
        context.startActivity(intent);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = getIntent().getStringExtra(MENU_TOPIC_URL);
        getTopicIntentExtra();
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(WebViewActivity.this, "url null", Toast.LENGTH_LONG).show();
            WebViewActivity.this.finish();
            return;
        }

        WebView web = getWebView();
        setContentView(web);

        initActionBar();

        web.getSettings().setJavaScriptEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });
        web.loadUrl(url);
    }

    private void getTopicIntentExtra() {
        if (getIntent().hasExtra(TOPIC_CHATMESSAGE)) {
            mChatMessage = getIntent().getParcelableExtra(TOPIC_CHATMESSAGE);
        }
        if (getIntent().hasExtra(TOPIC_TOPICCONTENT)) {
            mTopicContent = getIntent().getParcelableExtra(TOPIC_TOPICCONTENT);
        }
    }

    private void initActionBar() {
        ActionBar mActionBar = getActionBar();
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setTitle(R.string.webview_title);
        String title = getIntent().getStringExtra(MENU_TOPIC_TITLE);
        if (!TextUtils.isEmpty(title))
            mActionBar.setTitle(title);
    }

    private WebView getWebView() {
        LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        WebView webView = new WebView(WebViewActivity.this);
        webView.setLayoutParams(param);
        return webView;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case MENU_FORWARD_MSG:
                PASendMessageUtil.rcsForwardMessage(this, mChatMessage);
                return true;
            case MENU_COPY_LINK:
                PASendMessageUtil.rcsCopyMessageLink(this, mTopicContent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.clear();
        if (mChatMessage != null) {
            menu.add(0, MENU_FORWARD_MSG, Menu.NONE, R.string.menu_forward_msg).setShowAsAction(
                    MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(0, MENU_COPY_LINK, Menu.NONE, R.string.menu_copy_link).setShowAsAction(
                    MenuItem.SHOW_AS_ACTION_NEVER);
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case PAConversationActivity.REQUEST_SELECT_GROUP:
            case PAConversationActivity.REQUEST_CODE_RCS_PICK:
                if (data != null) {
                    ArrayList<String> numbers = data.getStringArrayListExtra("recipients");
                    PASendMessageUtil.forwardTopicToNumber(this, mTopicContent, numbers);
                }
                break;
            case PAConversationActivity.REQUEST_SELECT_CONV:
                if (data != null) {
                    PASendMessageUtil.forwardTopicToConversation(this, data, mTopicContent);
                }
                break;
            case PAConversationActivity.REQUEST_SELECT_PUBLIC_ACCOUNT:
                if (data != null) {
                    String pcId = data.getStringExtra("selectPublicId");
                    PASendMessageUtil.forwardRcsMessageToPublicAccount(this, pcId, mChatMessage);
                }
                break;
        }
    }
}
