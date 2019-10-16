AM_CFLAGS = -Wundef \
        -Wstrict-prototypes \
        -Wno-trigraphs \
        -g -O0 \
        -fno-inline \
        -fno-short-enums \
        -fpic

AM_CPPFLAGS = -I../inc \
              -I$(WORKSPACE)/common/inc

h_sources = ../inc/sound_trigger.h \
            ../inc/log.h

c_sources = smwrapper.c

lib_LTLIBRARIES = libsmwrapper.la
libsmwrapper_la_CC = @CC@
libsmwrapper_la_SOURCES = $(c_sources) $(h_sources)
libsmwrapper_la_CFLAGS = $(AM_CFLAGS) -DUSE_GLIB @GLIB_CFLAGS@ -D ETC_ROOT_PATH=\"/etc/\" -D DEFAULT_BOARD=\"MTP\"
libsmwrapper_la_CPPFLAGS = $(AM_CPPFLAGS) -DUSE_GLIB @GLIB_CFLAGS@
libsmwrapper_la_LDFLAGS = -pthread -ldl @GLIB_LIBS@ -shared -version-info 1:0:0
