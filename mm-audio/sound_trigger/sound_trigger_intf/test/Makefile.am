AM_CFLAGS = -Wundef \
        -Wstrict-prototypes \
        -Wno-trigraphs \
        -g -O0 \
        -fno-inline \
        -fno-short-enums \
        -fpic

AM_CPPFLAGS = -I../inc \
              -I../st_extn/inc \
               $(SOUNDTRIGGER_CFLAGS) \
               $(ACDBLOADER_CFLAGS) \
               $(AUDCAL_CFLAGS) \
              -I$(WORKSPACE)/common/inc

requiredlibs = -lc \
               -lrt \
               -pthread -ldl \
               ../src/libsoundtrigger.la \
               ../st_extn/libsoundtrigger_extn.la \
               $(SOUNDTRIGGER_LIBS) \
               $(ACDBLOADER_LIBS) \
               $(AUDCAL_LIBS) \
               -lasound

bin_PROGRAMS = sound_trigger_test
soundtriggertest_c_sources = sound_trigger_test.c \
                             ucm_helper.c \
                             acdb_helper.c

sound_trigger_test_CC = @CC@
sound_trigger_test_SOURCES = $(soundtriggertest_c_sources)
sound_trigger_test_CFLAGS = $(AM_CFLAGS) -DUSE_GLIB @GLIB_CFLAGS@ -D DEFAULT_BOARD=\"MTP\"
sound_trigger_test_CPPFLAGS = $(AM_CPPFLAGS) -DUSE_GLIB @GLIB_CFLAGS@
sound_trigger_test_LDFLAGS = $(requiredlibs) @GLIB_CFLAGS@ -shared -version-info 1:0:0
