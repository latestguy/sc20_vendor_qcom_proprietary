/*
 * Copyright (c) 2015 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 *
 */

int sound_trigger_extn_read(int capture_handle, void *buffer,
                       size_t bytes);
void sound_trigger_extn_stop_lab(int capture_handle);
int sound_trigger_extn_init(void );
void sound_trigger_extn_deinit(void );
