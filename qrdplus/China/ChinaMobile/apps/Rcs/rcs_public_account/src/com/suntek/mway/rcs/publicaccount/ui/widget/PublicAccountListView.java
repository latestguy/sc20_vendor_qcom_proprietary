/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.publicaccount.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ProgressBar;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView;
import android.widget.TextView;
import com.suntek.mway.rcs.publicaccount.R;

public class PublicAccountListView extends ListView implements OnScrollListener {
    private View mFooter;

    private TextView mLoadFull;

    private View mLoadView;

    private int mPageSize = 10;

    private boolean isLoading;

    private boolean isLoadFull;

    private boolean isLoadBefore = true;

    private boolean loadEnable = true;

    private int mFirstVisibleItem;

    private int mScrollState;

    private OnLoadListener onLoadListener;

    public PublicAccountListView(Context context) {
        super(context);
        initView(context);
    }

    public PublicAccountListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public PublicAccountListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context);
    }

    public void setOnLoadListener(OnLoadListener onLoadListener) {
        this.loadEnable = true;
        this.onLoadListener = onLoadListener;
        }

    public boolean isLoadEnable() {
        return loadEnable;
        }

    public void setLoadEnable(boolean loadEnable) {
        this.loadEnable = loadEnable;
        this.removeFooterView(mFooter);
    }

    public int getPageSize() {
        return mPageSize;
    }

    public void setPageSize(int pageSize) {
        this.mPageSize = pageSize;
    }

    private void initView(Context context) {
        mFooter = LayoutInflater.from(context).inflate(R.layout.pull_to_load_footer, null);
        mLoadFull = (TextView) mFooter.findViewById(R.id.loadfull);
        mLoadView = (View) mFooter.findViewById(R.id.loadView);
        this.addFooterView(mFooter);
        this.setOnScrollListener(this);
    }

    public void onLoad() {
        if (onLoadListener != null) {
            onLoadListener.onLoad();
        }
    }

    public void onLoadComplete() {
        isLoading = false;
    }

    public void onLoadAgain() {
        isLoadFull = false;
    }

    public void onLoadFull() {
        isLoadFull = true;
        setResultSize();
    }

    public void showFooter() {
        setResultSize();
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
            int visibleItemCount, int totalItemCount) {
        this.mFirstVisibleItem = firstVisibleItem;
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        this.mScrollState = scrollState;
        ifNeedLoad(view, mScrollState);
    }

    private void ifNeedLoad(AbsListView view, int scrollState) {
        if (!loadEnable) {
            return;
        }
        try {
            if (scrollState == OnScrollListener.SCROLL_STATE_IDLE && !isLoading
                    && view.getLastVisiblePosition() == view
                    .getPositionForView(mFooter) && !isLoadFull) {
                onLoad();
                isLoading = true;
            }
        } catch (Exception e) {
        }
    }

    public void setResultSize() {
        if (isLoadFull) {
            mLoadFull.setVisibility(View.VISIBLE);
            mLoadView.setVisibility(View.GONE);
        } else {
            mLoadFull.setVisibility(View.GONE);
            mLoadView.setVisibility(View.VISIBLE);
        }
    }

    public interface OnLoadListener {
        public void onLoad();
    }
}
