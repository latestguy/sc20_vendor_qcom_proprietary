AM_CFLAGS = -Wundef \
        -Wstrict-prototypes \
        -Wno-trigraphs \
        -g -O0 \
        -fno-inline \
        -fno-short-enums \
        -fpic

AM_CPPFLAGS = -Iinc \
              -I../inc \
              -I$(WORKSPACE)/common/inc

h_sources = sound_trigger_extn.h

c_sources = sound_trigger_extn.c

lib_LTLIBRARIES = libsoundtrigger_extn.la
libsoundtrigger_extn_la_CC = @CC@
libsoundtrigger_extn_la_SOURCES = $(c_sources) $(h_sources)
libsoundtrigger_extn_la_CFLAGS = $(AM_CFLAGS) -DUSE_GLIB @GLIB_CFLAGS@ -D ETC_ROOT_PATH=\"/etc/\" -D DEFAULT_BOARD=\"MTP\"
libsoundtrigger_extn_la_CPPFLAGS = $(AM_CPPFLAGS) -DUSE_GLIB @GLIB_CFLAGS@
libsoundtrigger_extn_la_LDFLAGS = -pthread -ldl @GLIB_LIBS@ -shared -version-info 1:0:0
