/**
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc
 */
package com.oma.drm.demo;

import android.content.Context;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.oma.drm.demo.DrmItemFragment.OnListFragmentInteractionListener;

/**
 * {@link RecyclerView.Adapter} that can display a {@link com.oma.drm.demo.DrmItemRecyclerViewAdapter.DrmItem} and makes a call to the
 * specified {@link OnListFragmentInteractionListener}.
 * TODO: Replace the implementation with code for your data type.
 */
public class DrmItemRecyclerViewAdapter extends RecyclerView.Adapter<DrmItemRecyclerViewAdapter.ViewHolder>
        implements PopupMenu.OnMenuItemClickListener {

    private final OnListFragmentInteractionListener mListener;

    private Context mContext;

    private int mPosition;

    private Cursor mCursor;

    private boolean mDataValid;

    private int mRowIdColumn;

    private DataSetObserver mDataSetObserver;

    public DrmItemRecyclerViewAdapter(Context context, Cursor cursor, OnListFragmentInteractionListener listener) {
        mListener = listener;
        mContext = context;
        mCursor = cursor;
        mDataValid = cursor != null;
        mRowIdColumn = mDataValid ? mCursor.getColumnIndex("_id") : -1;
        mDataSetObserver = new NotifyingDataSetObserver();
        if (mCursor != null) {
            mCursor.registerDataSetObserver(mDataSetObserver);
        }
    }


    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        if (!mDataValid) {
            throw new IllegalStateException("this should only be called when the cursor is valid");
        }
        if (!mCursor.moveToPosition(position)) {
            throw new IllegalStateException("couldn't move cursor to position " + position);
        }
        holder.mItem = DrmItem.fromCursor(mCursor);
        holder.mTextTitle.setText(holder.mItem.mDisplayName);
        String mime = holder.mItem.mMimeType;

        if (!TextUtils.isEmpty(mime)) {
            if (holder.mItem.mMimeType.startsWith(DrmItem.TYPE_IMAGE)) {
                holder.mImageIcon.setImageResource(R.drawable.ic_photo_black_24dp);
            } else if (holder.mItem.mMimeType.startsWith(DrmItem.TYPE_AUDIO)) {
                holder.mImageIcon.setImageResource(R.drawable.ic_audiotrack_black_24dp);

            } else if (holder.mItem.mMimeType.startsWith(DrmItem.TYPE_VIDEO)) {
                holder.mImageIcon.setImageResource(R.drawable.ic_movie_black_24dp);
            } else {
                holder.mImageIcon.setImageResource(R.drawable.ic_video_library_black_24dp);
            }
        }

        holder.mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mListener) {
                    // Notify the active callbacks interface (the activity, if the
                    // fragment is attached to one) that an item has been selected.
                    mListener.onListFragmentInteraction(holder.mItem, OnListFragmentInteractionListener.ACTION_OPEN);
                }
            }
        });

        holder.mImagePopup.setTag(mCursor.getPosition());
        holder.mImagePopup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPosition = (int) v.getTag();
                showPopup(v, holder.mItem.mData.contains(".dcf"));
            }
        });
    }

    public void showPopup(View v, boolean share) {
        PopupMenu popup = new PopupMenu(mContext, v);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.popup_drm_item, popup.getMenu());
        popup.setOnMenuItemClickListener(this);
        if (!share) {
            Menu popupMenu = popup.getMenu();
            popupMenu.findItem(R.id.menu_popup_share).setEnabled(false);
        }
        popup.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        Cursor cursor = getCursor();
        cursor.moveToPosition(getPosition());
        DrmItem item = DrmItem.fromCursor(cursor);
        switch (menuItem.getItemId()) {
            case R.id.menu_popup_open:
                if (null != mListener) {
                    mListener.onListFragmentInteraction(item, OnListFragmentInteractionListener.ACTION_OPEN);
                }
                break;
            case R.id.menu_popup_share:
                if (null != mListener) {
                    mListener.onListFragmentInteraction(item, OnListFragmentInteractionListener.ACTION_SHARE);
                }
                break;
            case R.id.menu_popup_details:
                if (null != mListener) {
                    mListener.onListFragmentInteraction(item, OnListFragmentInteractionListener.ACTION_DETAILS);
                }
                break;
            default:
                break;
        }
        return false;
    }

    public int getPosition() {
        return mPosition;
    }


    public Cursor getCursor() {
        return mCursor;
    }

    @Override
    public int getItemCount() {
        if (mDataValid && mCursor != null) {
            return mCursor.getCount();
        }
        return 0;
    }

    @Override
    public long getItemId(int position) {
        if (mDataValid && mCursor != null && mCursor.moveToPosition(position)) {
            return mCursor.getLong(mRowIdColumn);
        }
        return 0;
    }

    @Override
    public void setHasStableIds(boolean hasStableIds) {
        super.setHasStableIds(true);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_drm_item, parent, false);
        return new ViewHolder(view);
    }


    public void changeCursor(Cursor cursor) {
        Cursor old = swapCursor(cursor);
        if (old != null) {
            old.close();
        }
    }

    /**
     * Swap in a new Cursor, returning the old Cursor.  Unlike
     * {@link #changeCursor(Cursor)}, the returned old Cursor is <em>not</em>
     * closed.
     */
    public Cursor swapCursor(Cursor newCursor) {
        if (newCursor == mCursor) {
            return null;
        }
        final Cursor oldCursor = mCursor;
        if (oldCursor != null && mDataSetObserver != null) {
            oldCursor.unregisterDataSetObserver(mDataSetObserver);
        }
        mCursor = newCursor;
        if (mCursor != null) {
            if (mDataSetObserver != null) {
                mCursor.registerDataSetObserver(mDataSetObserver);
            }
            mRowIdColumn = newCursor.getColumnIndexOrThrow("_id");
            mDataValid = true;
            notifyDataSetChanged();
        } else {
            mRowIdColumn = -1;
            mDataValid = false;
            notifyDataSetChanged();
            //There is no notifyDataSetInvalidated() method in RecyclerView.Adapter
        }
        return oldCursor;
    }

    private class NotifyingDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            super.onChanged();
            mDataValid = true;
            notifyDataSetChanged();
        }

        @Override
        public void onInvalidated() {
            super.onInvalidated();
            mDataValid = false;
            notifyDataSetChanged();
            //There is no notifyDataSetInvalidated() method in RecyclerView.Adapter
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView mTextTitle;
        public final ImageView mImageIcon;
        public final ImageView mImagePopup;
        public DrmItem mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mTextTitle = (TextView) view.findViewById(R.id.text_title);
            mImageIcon = (ImageView) view.findViewById(R.id.image_icon);
            mImagePopup = (ImageView) view.findViewById(R.id.image_popup);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mTextTitle.getText() + "'";
        }
    }

    public static class DrmItem {
        public static final Uri CONTENT_URI_EXTERNAL_FILE = Uri.parse("content://media/external/file/");

        public static final String TYPE_IMAGE = "drm+container_based+image/";
        public static final String TYPE_AUDIO = "audio/";
        public static final String TYPE_VIDEO = "video/";

        public long mId;
        public String mDisplayName;
        public String mTitle;
        public String mMimeType;
        public String mData;
        public int mMediaType; //  MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE


        public static final DrmItem fromCursor(Cursor cursor) {
            DrmItem item = new DrmItem();
            item.mId = cursor.getInt(cursor.getColumnIndex(MediaStore.Files.FileColumns._ID));
            item.mTitle = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.TITLE));
            item.mMimeType = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE));
            item.mDisplayName = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME));
            item.mData = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA));
            item.mMediaType = cursor.getInt(cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE));

            if (TextUtils.isEmpty(item.mDisplayName) && !TextUtils.isEmpty(item.mData)) {
                item.mDisplayName = item.mData.subSequence(item.mData.lastIndexOf("/") + 1, item.mData.length()).toString();
            }
            return item;
        }


    }

}
