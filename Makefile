CC = gcc
CFLAGS = -Wall -g
CPPFLAGS = -D_GNU_SOURCE -I.
JAVAH = javah
JAVAC = javac

# Java class files
JAVASRCS = $(shell find -name \*.java)
CLASSES = $(patsubst %.java,%.class,$(JAVASRCS))

# JNI shared library
JNILIB = libhurd-java.so
JNISRCS = $(shell find -name \*.c)
JNIHDRS = $(patsubst %.c,%.h,$(JNISRCS))
JNIOBJS = $(patsubst %.c,%.o,$(JNISRCS))

all: test

$(CLASSES): $(JAVASRCS)
	$(JAVAC) $(JAVASRCS)

%.h: %.class
	$(JAVAH) -cp . -o $@.n $*
	mv $@.n $@

%.o: %.c
	$(CC) $(CFLAGS) $(CPPFLAGS) -o $@ -c $<

$(JNILIB): $(JNIOBJS)
	$(CC) $(CFLAGS) -shared -o $@ $(JNIOBJS)

test: $(CLASSES) $(JNILIB)
	echo $(JAVASRCS)
	LD_LIBRARY_PATH=. java HelloMach

clean:
	$(RM) $(CLASSES) $(JNILIB) $(JNIOBJS) $(JNIHDRS)

$(JNIOBJS): $(JNIHDRS)
.PRECIOUS: %.h
