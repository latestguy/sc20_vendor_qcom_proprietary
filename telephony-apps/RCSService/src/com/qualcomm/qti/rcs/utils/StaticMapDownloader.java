/**
 * Copyright (c) 2016 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 **/

package com.qualcomm.qti.rcs.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.os.AsyncTask;
import android.util.Log;

import com.qualcomm.qti.rcs.RcsService;

/**
 * StaticMapDownloader will download the google map image using HTTP request.
 * this class will asynchronously download the google map image with the
 * specified width and height. once the image is downloaded then using
 * StaticMapDownloadListener listener the result will be sent back.
 *
 * Note: The map key has to be added in strings.
 */
public class StaticMapDownloader extends
        AsyncTask<com.qualcomm.qti.rcs.utils.StaticMapDownloader.StaticMapParam, Void, byte[]> {

    public static class StaticMapParam {
        double latitude, longitude;
        int width, height;

        public StaticMapParam(double lat, double lon, int width, int height) {
            this.latitude = lat;
            this.longitude = lon;
            this.width = width;
            this.height = height;
        }
    }

    public static interface StaticMapDownloadListener {
        public void onStaticMapDownloaded(byte[] arr);
    }

    private static final boolean DBG = RcsService.DBG;
    private static final String LOG_TAG = "StaticMapDownloader";

    private static final String BASE_URL = "https://maps.googleapis.com/maps/api/staticmap?";
    private static final String PARAM_CENTER = "center";
    private static final String PARAM_KEY = "key";
    private static final String PARAM_MARKERS = "markers";
    private static final String PARAM_SIZE = "size";
    private static final String PARAM_ZOOM = "zoom";

    private static final String MARKER_STYPE = "color";
    private static final String MARKER_COLOR_RED = "red";
    private static final String ZOOM_LEVEL = "19";

    private static final String EQUAL = "=";
    private static final String OR = "|";
    private static final String COLON = ":";
    private static final String COMMA = ",";
    private static final String AND = "&";

    private StaticMapDownloadListener mListener;
    private String mKey = null;

    public StaticMapDownloader(StaticMapDownloadListener listener, String key) {
        mListener = listener;
        mKey = key;
    }

    /**
     * getMarker will return the marker related param value.
     *
     * @return string of marker
     */
    private String getMarker(StaticMapParam param) {
        StringBuilder builder = new StringBuilder();
        builder.append(PARAM_MARKERS);
        builder.append(EQUAL);
        builder.append(MARKER_STYPE);
        builder.append(COLON);
        builder.append(MARKER_COLOR_RED);
        builder.append(OR);
        builder.append(param.latitude);
        builder.append(COMMA);
        builder.append(param.longitude);
        return builder.toString();
    }

    /**
     * getCenter will return the center related param value.
     *
     * @return string of center value
     */
    private String getCenter(StaticMapParam param) {
        StringBuilder builder = new StringBuilder();
        builder.append(PARAM_CENTER);
        builder.append(EQUAL);
        builder.append(param.latitude);
        builder.append(COMMA);
        builder.append(param.longitude);
        return builder.toString();
    }

    /**
     * getKey will return the google map related param value.
     *
     * @return string of key value
     */
    private String getKey() {
        StringBuilder builder = new StringBuilder();
        builder.append(PARAM_KEY);
        builder.append(EQUAL);
        builder.append(mKey);
        return builder.toString();
    }

    /**
     * getZoom will return the zoom related param value.
     *
     * @return string of zoom value
     */
    private String getZoom() {
        StringBuilder builder = new StringBuilder();
        builder.append(PARAM_ZOOM);
        builder.append(EQUAL);
        builder.append(ZOOM_LEVEL);
        return builder.toString();
    }

    /**
     * getsize will return the size related param value.
     *
     * @return string of size value
     */
    private String getSize(StaticMapParam param) {
        StringBuilder builder = new StringBuilder();
        builder.append(PARAM_SIZE);
        builder.append(EQUAL);
        builder.append(param.width);
        builder.append("x");
        builder.append(param.height);
        return builder.toString();
    }

    /**
     * geturl will return the url.
     *
     * @return string of url string.
     */
    private String getUrl(StaticMapParam param) {
        StringBuilder builder = new StringBuilder();
        builder.append(BASE_URL);
        builder.append(getCenter(param));
        builder.append(AND);
        builder.append(getMarker(param));
        builder.append(AND);
        builder.append(getKey());
        builder.append(AND);
        builder.append(getSize(param));
        builder.append(AND);
        builder.append(getZoom());
        return builder.toString();
    }

    @Override
    protected byte[] doInBackground(StaticMapParam... params) {
        if (params != null) {
            StaticMapParam param = params[0];
            return getStaticMapImage(param);
        }
        return null;
    }

    @Override
    protected void onPostExecute(byte[] result) {
        log("onPostExecute : " + result);
        // TODO Auto-generated method stub
        super.onPostExecute(result);
        if (mListener != null) {
            log("calling  : onStaticMapDownloaded");
            mListener.onStaticMapDownloaded(result);
        }
    }

    /**
     * read the inputstream and convert it to byte array
     *
     * @return byte array.
     */
    private byte[] readBytes(InputStream inputStream) throws IOException {
        // this dynamically extends to take the bytes you read
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        // this is storage overwritten on each iteration with bytes
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        // we need to know how may bytes were read to write them to the
        // byteBuffer
        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        // and then we can return your byte array.
        return byteBuffer.toByteArray();
    }

    /**
     * Download the image and return the byte array
     *
     * @return byte array.
     */
    private byte[] getStaticMapImage(StaticMapParam param) {
        String url = getUrl(param);
        log("URL :" + url);
        HttpURLConnection urlConnection = null;
        try {
            URL uri = new URL(url);
            urlConnection = (HttpURLConnection) uri.openConnection();
            InputStream inputStream = urlConnection.getInputStream();
            return readBytes(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
            log("ERROR :" + e.getMessage());
        }
        return null;
    }

    private static void log(String message) {
        if (DBG)
            Log.d(LOG_TAG, message);
    }
}
