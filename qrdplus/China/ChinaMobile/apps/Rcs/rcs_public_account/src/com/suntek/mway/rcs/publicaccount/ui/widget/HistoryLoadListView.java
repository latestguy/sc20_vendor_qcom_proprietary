/*
 * Copyright (c) 2015 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 */

package com.suntek.mway.rcs.publicaccount.ui.widget;

import com.suntek.mway.rcs.publicaccount.R;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ProgressBar;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView;
import android.widget.TextView;

public class HistoryLoadListView extends ListView implements OnScrollListener {
    private View mFooter;

    private TextView mLoadFull;

    private TextView mLoading;

    private ProgressBar mLoadingBar;

    private int mPageSize = 10;

    private boolean isLoading;

    private boolean isLoadFull;

    private boolean loadEnable = true;

    private int mFirstVisibleItem;

    private int mScrollState;

    private OnLoadListener onLoadListener;

    public HistoryLoadListView(Context context) {
        super(context);
        initView(context);
    }

    public HistoryLoadListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public HistoryLoadListView(Context context, AttributeSet attrs, int defStyle) {
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
        mLoading = (TextView) mFooter.findViewById(R.id.loading);
        mLoadingBar = (ProgressBar) mFooter.findViewById(R.id.loadingbar);

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

    public void onLoadFull() {
        isLoadFull = true;
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
            mLoading.setVisibility(View.GONE);
            mLoadingBar.setVisibility(View.GONE);
        } else {
            mLoadFull.setVisibility(View.GONE);
            mLoading.setVisibility(View.VISIBLE);
            mLoadingBar.setVisibility(View.VISIBLE);
        }
    }

    public interface OnLoadListener {
        public void onLoad();
    }
}
