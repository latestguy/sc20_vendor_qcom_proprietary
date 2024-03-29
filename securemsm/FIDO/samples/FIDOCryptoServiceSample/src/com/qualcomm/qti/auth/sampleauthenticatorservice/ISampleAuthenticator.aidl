/*
 * Copyright (c) 2015 Qualcomm Technologies, Inc.  All Rights Reserved.
 * Qualcomm Technologies Proprietary and Confidential.
 */

package com.qualcomm.qti.auth.sampleauthenticatorservice;

import com.qualcomm.qti.auth.sampleauthenticatorservice.ISampleAuthenticatorListener;

interface ISampleAuthenticator {
  int verifyUser(in byte[] nonce, in String secAppName, in ISampleAuthenticatorListener iSampleAuthenticatorListener);
  int cancel(in byte[] nonce, in ISampleAuthenticatorListener iSampleAuthenticatorListener);
  int getEnrollmentStatus(long userId, in ISampleAuthenticatorListener iSampleAuthenticatorListener);
}

