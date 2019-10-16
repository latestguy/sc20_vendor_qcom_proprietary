/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdbool.h>
#include <stdint.h>
#include <sys/cdefs.h>
#include <sys/types.h>

#define popcount __builtin_popcount

#define AUDIO_IO_HANDLE_NONE 0
typedef int audio_io_handle_t;

typedef enum {
    /* All of these are in native byte order */
    AUDIO_FORMAT_PCM_SUB_16_BIT          = 0x1, /* DO NOT CHANGE - PCM signed 16 bits */
    AUDIO_FORMAT_PCM_SUB_8_BIT           = 0x2, /* DO NOT CHANGE - PCM unsigned 8 bits */
    AUDIO_FORMAT_PCM_SUB_32_BIT          = 0x3, /* PCM signed .31 fixed point */
    AUDIO_FORMAT_PCM_SUB_8_24_BIT        = 0x4, /* PCM signed 8.23 fixed point */
    AUDIO_FORMAT_PCM_SUB_FLOAT           = 0x5, /* PCM single-precision floating point */
    AUDIO_FORMAT_PCM_SUB_24_BIT_PACKED   = 0x6, /* PCM signed .23 fixed point packed in 3 bytes */
} audio_format_pcm_sub_fmt_t;

typedef enum {
    AUDIO_FORMAT_INVALID             = 0xFFFFFFFFUL,
    AUDIO_FORMAT_DEFAULT             = 0,
    AUDIO_FORMAT_PCM                 = 0x00000000UL, /* DO NOT CHANGE */
    AUDIO_FORMAT_MP3                 = 0x01000000UL,
    AUDIO_FORMAT_AMR_NB              = 0x02000000UL,
    AUDIO_FORMAT_AMR_WB              = 0x03000000UL,
    AUDIO_FORMAT_AAC                 = 0x04000000UL,
    AUDIO_FORMAT_HE_AAC_V1           = 0x05000000UL,
    AUDIO_FORMAT_HE_AAC_V2           = 0x06000000UL,
    AUDIO_FORMAT_VORBIS              = 0x07000000UL,
    AUDIO_FORMAT_MAIN_MASK           = 0xFF000000UL,
    AUDIO_FORMAT_SUB_MASK            = 0x00FFFFFFUL,

    /* Aliases */
    AUDIO_FORMAT_PCM_16_BIT          = (AUDIO_FORMAT_PCM |
                                        AUDIO_FORMAT_PCM_SUB_16_BIT),
    AUDIO_FORMAT_PCM_8_BIT           = (AUDIO_FORMAT_PCM |
                                        AUDIO_FORMAT_PCM_SUB_8_BIT),
    AUDIO_FORMAT_PCM_32_BIT          = (AUDIO_FORMAT_PCM |
                                        AUDIO_FORMAT_PCM_SUB_32_BIT),
    AUDIO_FORMAT_PCM_8_24_BIT        = (AUDIO_FORMAT_PCM |
                                        AUDIO_FORMAT_PCM_SUB_8_24_BIT),
} audio_format_t;

enum {
    /* input channels */
    AUDIO_CHANNEL_IN_LEFT            = 0x4,
    AUDIO_CHANNEL_IN_RIGHT           = 0x8,
    AUDIO_CHANNEL_IN_FRONT           = 0x10,
    AUDIO_CHANNEL_IN_BACK            = 0x20,
    AUDIO_CHANNEL_IN_LEFT_PROCESSED  = 0x40,
    AUDIO_CHANNEL_IN_RIGHT_PROCESSED = 0x80,
    AUDIO_CHANNEL_IN_FRONT_PROCESSED = 0x100,
    AUDIO_CHANNEL_IN_BACK_PROCESSED  = 0x200,
    AUDIO_CHANNEL_IN_PRESSURE        = 0x400,
    AUDIO_CHANNEL_IN_X_AXIS          = 0x800,
    AUDIO_CHANNEL_IN_Y_AXIS          = 0x1000,
    AUDIO_CHANNEL_IN_Z_AXIS          = 0x2000,
    AUDIO_CHANNEL_IN_VOICE_UPLINK    = 0x4000,
    AUDIO_CHANNEL_IN_VOICE_DNLINK    = 0x8000,

    AUDIO_CHANNEL_IN_MONO   = AUDIO_CHANNEL_IN_FRONT,
    AUDIO_CHANNEL_IN_STEREO = (AUDIO_CHANNEL_IN_LEFT | AUDIO_CHANNEL_IN_RIGHT),
    AUDIO_CHANNEL_IN_FRONT_BACK = (AUDIO_CHANNEL_IN_FRONT | AUDIO_CHANNEL_IN_BACK),
    AUDIO_CHANNEL_IN_ALL    = (AUDIO_CHANNEL_IN_LEFT |
                               AUDIO_CHANNEL_IN_RIGHT |
                               AUDIO_CHANNEL_IN_FRONT |
                               AUDIO_CHANNEL_IN_BACK|
                               AUDIO_CHANNEL_IN_LEFT_PROCESSED |
                               AUDIO_CHANNEL_IN_RIGHT_PROCESSED |
                               AUDIO_CHANNEL_IN_FRONT_PROCESSED |
                               AUDIO_CHANNEL_IN_BACK_PROCESSED|
                               AUDIO_CHANNEL_IN_PRESSURE |
                               AUDIO_CHANNEL_IN_X_AXIS |
                               AUDIO_CHANNEL_IN_Y_AXIS |
                               AUDIO_CHANNEL_IN_Z_AXIS |
                               AUDIO_CHANNEL_IN_VOICE_UPLINK |
                               AUDIO_CHANNEL_IN_VOICE_DNLINK),
};

typedef uint32_t audio_channel_mask_t;

enum {
    AUDIO_DEVICE_NONE                          = 0x0,
    /* reserved bits */
    AUDIO_DEVICE_BIT_IN                        = 0x80000000,
    AUDIO_DEVICE_BIT_DEFAULT                   = 0x40000000,
    /* input devices */
    AUDIO_DEVICE_IN_COMMUNICATION         = AUDIO_DEVICE_BIT_IN | 0x1,
    AUDIO_DEVICE_IN_AMBIENT               = AUDIO_DEVICE_BIT_IN | 0x2,
    AUDIO_DEVICE_IN_BUILTIN_MIC           = AUDIO_DEVICE_BIT_IN | 0x4,
    AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET = AUDIO_DEVICE_BIT_IN | 0x8,
    AUDIO_DEVICE_IN_WIRED_HEADSET         = AUDIO_DEVICE_BIT_IN | 0x10,
    AUDIO_DEVICE_IN_AUX_DIGITAL           = AUDIO_DEVICE_BIT_IN | 0x20,
    AUDIO_DEVICE_IN_VOICE_CALL            = AUDIO_DEVICE_BIT_IN | 0x40,
    AUDIO_DEVICE_IN_BACK_MIC              = AUDIO_DEVICE_BIT_IN | 0x80,
    AUDIO_DEVICE_IN_REMOTE_SUBMIX         = AUDIO_DEVICE_BIT_IN | 0x100,
    AUDIO_DEVICE_IN_ANLG_DOCK_HEADSET     = AUDIO_DEVICE_BIT_IN | 0x200,
    AUDIO_DEVICE_IN_DGTL_DOCK_HEADSET     = AUDIO_DEVICE_BIT_IN | 0x400,
    AUDIO_DEVICE_IN_USB_ACCESSORY         = AUDIO_DEVICE_BIT_IN | 0x800,
    AUDIO_DEVICE_IN_USB_DEVICE            = AUDIO_DEVICE_BIT_IN | 0x1000,
    AUDIO_DEVICE_IN_DEFAULT               = AUDIO_DEVICE_BIT_IN | AUDIO_DEVICE_BIT_DEFAULT,

    AUDIO_DEVICE_IN_ALL     = (AUDIO_DEVICE_IN_COMMUNICATION |
                               AUDIO_DEVICE_IN_AMBIENT |
                               AUDIO_DEVICE_IN_BUILTIN_MIC |
                               AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET |
                               AUDIO_DEVICE_IN_WIRED_HEADSET |
                               AUDIO_DEVICE_IN_AUX_DIGITAL |
                               AUDIO_DEVICE_IN_VOICE_CALL |
                               AUDIO_DEVICE_IN_BACK_MIC |
                               AUDIO_DEVICE_IN_REMOTE_SUBMIX |
                               AUDIO_DEVICE_IN_ANLG_DOCK_HEADSET |
                               AUDIO_DEVICE_IN_DGTL_DOCK_HEADSET |
                               AUDIO_DEVICE_IN_USB_ACCESSORY |
                               AUDIO_DEVICE_IN_USB_DEVICE |
                               AUDIO_DEVICE_IN_DEFAULT),
    AUDIO_DEVICE_IN_ALL_SCO = AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET,
};

typedef uint32_t audio_devices_t;

static inline bool audio_is_input_device(audio_devices_t device)
{
    if ((device & AUDIO_DEVICE_BIT_IN) != 0) {
        device &= ~AUDIO_DEVICE_BIT_IN;
        if ((popcount(device) == 1) && ((device & ~AUDIO_DEVICE_IN_ALL) == 0))
            return true;
    }
    return false;
}

typedef enum {
    AUDIO_STREAM_DEFAULT          = -1,
    AUDIO_STREAM_VOICE_CALL       = 0,
    AUDIO_STREAM_SYSTEM           = 1,
    AUDIO_STREAM_RING             = 2,
    AUDIO_STREAM_MUSIC            = 3,
    AUDIO_STREAM_ALARM            = 4,
    AUDIO_STREAM_NOTIFICATION     = 5,
    AUDIO_STREAM_BLUETOOTH_SCO    = 6,
    AUDIO_STREAM_ENFORCED_AUDIBLE = 7, /* Sounds that cannot be muted by user and must be routed to speaker */
    AUDIO_STREAM_DTMF             = 8,
    AUDIO_STREAM_TTS              = 9,

    AUDIO_STREAM_CNT,
    AUDIO_STREAM_MAX              = AUDIO_STREAM_CNT - 1,
} audio_stream_type_t;

typedef struct {
    uint16_t version;                   // version of the info structure
    uint16_t size;                      // total size of the structure including version and size
    uint32_t sample_rate;               // sample rate in Hz
    audio_channel_mask_t channel_mask;  // channel mask
    audio_format_t format;              // audio format
    audio_stream_type_t stream_type;    // stream type
    uint32_t bit_rate;                  // bit rate in bits per second
    int64_t duration_us;                // duration in microseconds, -1 if unknown
    bool has_video;                     // true if stream is tied to a video stream
    bool is_streaming;                  // true if streaming, false if local playback
} audio_offload_info_t;

struct audio_config {
    uint32_t sample_rate;
    audio_channel_mask_t channel_mask;
    audio_format_t  format;
    audio_offload_info_t offload_info;
};

typedef struct audio_config audio_config_t;
