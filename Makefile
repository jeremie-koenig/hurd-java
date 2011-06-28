CC = gcc
CFLAGS = -Wall -g
JAVAH = javah
JAVAC = javac

all: test

%.class: %.java
	$(JAVAC) $<

%.h: %.class
	$(JAVAH) -cp . $*

lib%.so: %.c %.h
	$(CC) $(CFLAGS) -shared -o $@ $<

test: HelloMach.class libHelloMach.so
	LD_LIBRARY_PATH=. java HelloMach

clean:
	$(RM) HelloMach.class HelloMach.h libHelloMach.so

.PRECIOUS: %.h
