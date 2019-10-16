/**
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc
 */
package com.oma.drm.demo;

import com.oma.drm.demo.DrmItemRecyclerViewAdapter.DrmItem;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, DrmItemFragment.OnListFragmentInteractionListener {
    private static final String TAG = "OmaDrmDemo:MainActivity";
    private static final int TOKEN_DRM_QUERY = 1000;
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 100;

    private ViewGroup mMainContainer;
    private FragmentManager mFragmentManager;
    private DrmItemFragment mActiveFragment;
    private MyAsyncQueryHandler mQueryHandler;
    private String mSelection;
    private Uri mUri = MediaStore.Files.getContentUri("external");

    private String[] mProjection = {
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.TITLE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DISPLAY_NAME
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mQueryHandler = new MyAsyncQueryHandler(getContentResolver());

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);


        mMainContainer = (ViewGroup) findViewById(R.id.layout_main_container);
        mActiveFragment = DrmItemFragment.newInstance(1);
        mFragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
        fragmentTransaction.add(R.id.layout_main_container, mActiveFragment);
        fragmentTransaction.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startAllDrmMediaQuery();
    }


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {

        int id = item.getItemId();
        switch (id) {
            case R.id.nav_all:
                startAllDrmMediaQuery();
                break;
            case R.id.nav_image:
                startImageDrmMediaQuery();
                break;
            case R.id.nav_audio:
                startAudioDrmMediaQuery();
                break;
            case R.id.nav_video:
                startVideoDrmMediaQuery();
                break;
            default:
                startAllDrmMediaQuery();
                break;
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void startAllDrmMediaQuery() {
        String selection = "is_drm = 1";
        startDrmQuery(selection);

    }

    private void startImageDrmMediaQuery() {
        String selection = MediaStore.Files.FileColumns.MIME_TYPE + " LIKE '%"
                + DrmItemRecyclerViewAdapter.DrmItem.TYPE_IMAGE + "%'" + " AND  is_drm  = 1";
        startDrmQuery(selection);
    }

    private void startAudioDrmMediaQuery() {
        String selection = MediaStore.Files.FileColumns.MIME_TYPE + " LIKE '%"
                + DrmItemRecyclerViewAdapter.DrmItem.TYPE_AUDIO + "%'" + " AND  is_drm  = 1";
        startDrmQuery(selection);
    }

    private void startVideoDrmMediaQuery() {
        String selection = MediaStore.Files.FileColumns.MIME_TYPE + " LIKE '%"
                + DrmItemRecyclerViewAdapter.DrmItem.TYPE_VIDEO + "%'" + " AND  is_drm  = 1";
        startDrmQuery(selection);
    }

    private void startDrmQuery(String selection) {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permision for external storage read present");
            doQuery(selection);
        } else {
            Log.i(TAG, "Permision for external storage read not present");
            requestPermission();
            mSelection = selection;
        }
    }

    private void doQuery(String selection) {
        mQueryHandler.cancelOperation(TOKEN_DRM_QUERY);
        mQueryHandler.startQuery(TOKEN_DRM_QUERY, null, mUri, mProjection,
                selection, null, null);
    }

    private void requestPermission() {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
                    REQUEST_READ_EXTERNAL_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String permissions[], int[] grantResults) {
        if (requestCode == REQUEST_READ_EXTERNAL_STORAGE) {
            doQuery(mSelection);
        }
    }

    @Override
    public void onListFragmentInteraction(DrmItemRecyclerViewAdapter.DrmItem item, int action) {
        switch (action) {
            case DrmItemFragment.OnListFragmentInteractionListener.ACTION_SHARE:
                shareDrmItem(item);
                break;
            case DrmItemFragment.OnListFragmentInteractionListener.ACTION_DETAILS:
                viewDetailsDrmItem(item);
                break;
            case DrmItemFragment.OnListFragmentInteractionListener.ACTION_OPEN:
            default:
                openDrmItem(item);
                break;
        }
    }

    private void openDrmItem(DrmItemRecyclerViewAdapter.DrmItem item) {
        Uri uri = ContentUris.withAppendedId(
                DrmItemRecyclerViewAdapter.DrmItem.CONTENT_URI_EXTERNAL_FILE, item.mId);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(uri, item.mMimeType);
        intent.addCategory("android.intent.category.BROWSABLE");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Activity not found " + e);
        }
    }

    private void shareDrmItem(DrmItemRecyclerViewAdapter.DrmItem item) {
        Uri uri = ContentUris.withAppendedId(DrmItemRecyclerViewAdapter.DrmItem.CONTENT_URI_EXTERNAL_FILE, item.mId);
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        sharingIntent.setType(item.mMimeType);
        sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
        startActivity(Intent.createChooser(sharingIntent, "Share DRM File using"));
    }

    private void viewDetailsDrmItem(DrmItemRecyclerViewAdapter.DrmItem item) {
        Uri uri = ContentUris.withAppendedId(
                DrmItemRecyclerViewAdapter.DrmItem.CONTENT_URI_EXTERNAL_FILE,
                item.mId);
        Intent detailsIntent = new Intent("com.oma.drm.action.ACTION_DETAILS");
        detailsIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        detailsIntent.setComponent(new ComponentName("com.oma.drm",
                "com.oma.drm.ui.DrmDetailsDialogActivity"));
        detailsIntent.setData(uri);
        startActivity(detailsIntent);
    }

    class MyAsyncQueryHandler extends AsyncQueryHandler {

        public MyAsyncQueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (cursor != null) {
                mActiveFragment.changeCursor(cursor);
            }

        }
    }
}
