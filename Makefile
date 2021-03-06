CC = gcc
CFLAGS = -Wall -g
CPPFLAGS = -D_GNU_SOURCE -I.
JAVA_PREFIX =
JAVAH = $(JAVA_PREFIX)javah
JAVAC = $(JAVA_PREFIX)javac
JAVA = LD_LIBRARY_PATH=.$${LD_LIBRARY_PATH:+:$$LD_LIBRARY_PATH} java
JAVADOC = $(JAVA_PREFIX)javadoc
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
	$(JAVAH) -cp . -o '$@.n' org.gnu.$(subst /,.,'$*')
	mv '$@.n' '$@'

%.o: %.c
	$(CC) $(CFLAGS) $(CPPFLAGS) -o $@ -c $<

$(JNILIB): $(JNIOBJS)
	$(CC) $(CFLAGS) -shared -o $@ $(JNIOBJS)

test: $(CLASSES) $(JNILIB)
	echo $(JAVASRCS)
	$(JAVA) HelloMach

clean:
	$(RM) $(JNILIB) $(JNIOBJS) $(patsubst %,'%',$(JNIHDRS))
	find -name \*.class | xargs $(RM)

$(JNIOBJS): $(JNIHDRS)
.PRECIOUS: %.h
