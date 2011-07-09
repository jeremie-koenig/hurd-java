CC = gcc
CFLAGS = -Wall -g
CPPFLAGS = -D_GNU_SOURCE -I.
JAVAH = javah
JAVAC = javac
JAVADOC = javadoc
JAVADOCFLAGS = -use

# Java class files
JAVASRCS = $(shell find -name \*.java)
CLASSES = $(patsubst %.java,%.class,$(JAVASRCS))

# JNI shared library
JNILIB = libhurd-java.so
JNISRCS = $(shell find -name \*.c)
JNIHDRS = mach/Mach.h mach/Mach$$Port.h hurd/Hurd.h

JNIOBJS = $(patsubst %.c,%.o,$(JNISRCS))

all: test

$(CLASSES): $(JAVASRCS)
	$(JAVAC) $(JAVASRCS)

doc: $(JAVASRCS)
	#$(JAVADOC) $(JAVADOCFLAGS) -d $@.n $(JAVASRCS)
	# Use doxygen for now
	doxygen
	$(RM) -r $@
	mv $@.n $@

%.h: $(CLASSES)
	$(JAVAH) -cp . -o '$@.n' '$*'
	mv '$@.n' '$@'

%.o: %.c
	$(CC) $(CFLAGS) $(CPPFLAGS) -o $@ -c $<

$(JNILIB): $(JNIOBJS)
	$(CC) $(CFLAGS) -shared -o $@ $(JNIOBJS)

test: $(CLASSES) $(JNILIB)
	echo $(JAVASRCS)
	LD_LIBRARY_PATH=. java HelloMach

clean:
	$(RM) $(JNILIB) $(JNIOBJS) $(patsubst %,'%',$(JNIHDRS))
	find -name \*.class | xargs $(RM)

$(JNIOBJS): $(JNIHDRS)
.PRECIOUS: %.h
