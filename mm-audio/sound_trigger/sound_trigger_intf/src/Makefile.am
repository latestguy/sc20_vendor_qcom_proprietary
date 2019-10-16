AM_CFLAGS = -Wundef \
        -Wstrict-prototypes \
        -Wno-trigraphs \
        -g -O0 \
        -fno-inline \
        -fno-short-enums \
        -fpic

AM_CPPFLAGS = -I../inc \
              -I$(WORKSPACE)/common/inc

h_sources = ../inc/sound_trigger_hw.h \
            ../inc/sound_trigger_platform.h \
            ../inc/sound_trigger_prop_intf.h \
            ../inc/sound_trigger.h \
            ../inc/audio.h \
            ../inc/asoundlib.h \
            ../inc/log.h

c_sources = sound_trigger_hw.c \
            sound_trigger_platform.c \
            pcm.c

#sound_trigger_transition.c

library_includedir = $(includedir)
library_include_HEADERS = $(h_sources)

lib_LTLIBRARIES = libsoundtrigger.la
libsoundtrigger_la_CC = @CC@
libsoundtrigger_la_SOURCES = $(c_sources) $(h_sources)
libsoundtrigger_la_CFLAGS = $(AM_CFLAGS) -DUSE_GLIB @GLIB_CFLAGS@ -D ETC_ROOT_PATH=\"/etc/\" -D DEFAULT_BOARD=\"MTP\"
libsoundtrigger_la_CPPFLAGS = $(AM_CPPFLAGS) -DUSE_GLIB @GLIB_CFLAGS@
libsoundtrigger_la_LDFLAGS = -pthread -ldl @GLIB_LIBS@ -shared -version-info 1:0:0
