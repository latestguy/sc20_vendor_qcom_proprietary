/**
 * Copyright (c) 2016 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 **/

package com.qualcomm.qti.rcs.utils;

import android.util.Log;
import com.qualcomm.qti.rcs.RcsService;
import org.codeaurora.rcscommon.EnrichedCallState;

/**
 * RcsUtils util class.
 */
public class RcsUtils {

    private static final boolean DBG = RcsService.DBG;
    private static final String TAG = "RcsUtils";

    /**
     * getLocalEnrichedCallState. gets the local EnrichedCallState enum.
     *
     * @param : EnrichedCallState, the state from the jio lib.
     * @return : EnrichedCallState. returns the corrosponding local
     *         EnrichedCallState enum.
     */
    public static EnrichedCallState getLocalEnrichedCallState(
            com.jio.join.enrichedcall.library.EnrichedCallState state) {

        EnrichedCallState localState = EnrichedCallState.UNKNOWN;
        switch(state) {
            case WAITING:
                localState = EnrichedCallState.WAITING;
                break;
            case FAILED:
                localState = EnrichedCallState.FAILED;
                break;
            case ESTABLISHED:
                localState = EnrichedCallState.ESTABLISHED;
                break;
        }
        return localState;
    }

    private static void log(String msg) {
        if (DBG) {
            Log.d(TAG, msg);
        }
    }
}
