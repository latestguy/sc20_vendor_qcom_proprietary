AM_CFLAGS = -Wall -g \
        -Wundef \
        -Wstrict-prototypes \
        -Wno-trigraphs \
        $(SECUREMSM-NOSHIP_CFLAGS)

ACLOCAL_AMFLAGS = -I m4

AM_CPPFLAGS = -D__packed__= \
		-DFEATURE_LE_DIAG \
		-DOE \
		$(SECUREMSMNOSHIP_CFLAGS)

bin_PROGRAMS = sampleclient
sampleclient_CC = @CC@
pkgconfigdir = $(libdir)/pkgconfig
sampleclient_SOURCES = qseecom_sample_client.c
sampleclient_CPPFLAGS = $(AM_CPPFLAGS)
sampleclient_CFLAGS = $(AM_CFLAGS)
sampleclient_LDFLAGS = -lpthread $(SECUREMSMNOSHIP_LIBS)
sampleclient_LDADD = $(SECUREMSMNOSHIP_LIBS)
