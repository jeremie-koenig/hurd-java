package org.gnu.mach;

import java.util.Collection;
import java.util.ArrayList;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;

/**
 * Safe Mach message buffer.
 *
 * <h3>Embedded port names and {@link MachPort} objects</h3>
 *
 * Mach messages store port names in their header and ini some of the
 * subsequent data items. To avoid port rights from being accidentally
 * deallocated or leaked, we must handle them carefully.
 *
 * When a {@link MachMsg} object is initially created, or after it has been
 * cleared, the header's port names are initialized to {@code MACH_PORT_NULL}
 * and no correponding {@link MachMsg} object exists. When port names are
 * added to the message contents through the {@link #setRemotePort} and
 * {@link #setLocalPort} methods or through one of the {@code put}
 * operations, on of two things can happen depending on the associated
 * {@link MachMsg.Type} used.
 *
 * If the port type is {@link MachMsg.Type#COPY_SEND}, {@link
 * MachMsg.Type#MAKE_SEND} or {@link MachMsg.Type#MAKE_SEND_ONCE}), the port
 * name is acquired using {@link MachPort#name()} and is kept as long as the
 * buffer contains the port name in question.
 *
 * If the port type is {@link MachMsg.Type#MOVE_RECEIVE}, {@link
 * MachMsg.Type#MOVE_SEND} or {@link MachMsg.Type#MOVE_SEND_ONCE}, the port
 * name is acquired using {@link MachPort#clear()}.
 */
public class MachMsg {
    /**
     * Type check exception.
     *
     * This exception is raised by {@code MachMsg.get*} operations when the
     * type descriptor read from the message does not match the expected
     * value.
     */
    public static class TypeCheckException extends Exception {
        static final long serialVersionUID = -8432763016000561949L;

        public TypeCheckException() {
            super();
        }
        public TypeCheckException(String msg) {
            super(msg);
        }
    }

    /**
     * Type descriptor for data items.
     *
     * This enumeration is used to write and check the type descriptors in a
     * Mach message. Each member represents a possible data type.  The
     * {@link #put} and {@link #get} methods read and write
     * {@code mach_msg_type_t} (or {@code mach_msg_long_type_t}) structures
     * from/to a given ByteBuffer object.
     *
     * In case an exception occurs, the buffer position can be anywhere
     * between its original value and the end of the type descriptor being
     * read or written. Users are advised to set the buffer's mark before
     * using these methods and restore it if an exception occurs.
     */
    public static enum Type {
        /* List of types currently in use. Extend as needed. */
        CHAR(8, 8, true),
        INTEGER_32(2, 32, false),
        INTEGER_64(11, 64, false),
        MOVE_RECEIVE(16, 32, false, true),
        MOVE_SEND(17, 32, false, true),
        MOVE_SEND_ONCE(18, 32, false, true),
        COPY_SEND(19, 32, false, false),
        MAKE_SEND(20, 32, false, false),
        MAKE_SEND_ONCE(21, 32, false, false);

        /* Aliases used for received ports. */
        public static final Type PORT_RECEIVE = MOVE_RECEIVE;
        public static final Type PORT_SEND = MOVE_SEND;
        public static final Type PORT_SEND_ONCE = MOVE_SEND_ONCE;

        /* Constants for mach_msg_type_t */
        private static final int BIT_INLINE     = 0x10000000;
        private static final int BIT_LONGFORM   = 0x20000000;
        private static final int BIT_DEALLOCATE = 0x40000000;
        private static final int CHECKED_BITS   = 0xf000ffff;

        /* Characteristics of this type */
        private int name;
        private int size;
        private boolean longform;
        private boolean port, deallocPort;

        /* Pre-constructed proto-header */
        private int header;

        /* Fill-in the data and build the proto-header */
        private Type(int name, int size, boolean longform) {
            this.name = name;
            this.size = size;
            this.longform = longform;

            header = longform ? BIT_LONGFORM : name | (size << 8);

            /* FIXME: for now we support only inline data. */
            header |= BIT_INLINE;
        }
        private Type(int name, int size, boolean longform, boolean dep) {
            this(name, size, longform);
            port = true;
            deallocPort = dep;
        }

        /** Get this type's name value. */
        public int value() { return name; }

        /** Whether this is a port type. */
        public boolean isPort() {
            return port;
        }

        /** Whether this is a deallocating port type. */
        public boolean isDeallocatedPort() {
            return deallocPort;
        }

        /* Check a value against the proto-header. */
        private void checkHeader(int header) throws TypeCheckException {
            if((header & CHECKED_BITS) != this.header)
                throw new TypeCheckException(String.format(
                            "Type check error (0x%x instead of 0x%x)",
                            header, this.header));
        }

        /* Check the remainder of a long form header. */
        private void checkLongHeader(int name, int size)
            throws TypeCheckException
        {
            if(name != this.name)
                throw new TypeCheckException(String.format(
                            "Type check error (name is 0x%x instead of 0x%x)",
                            name, this.name));
            if(size != this.size)
                throw new TypeCheckException(String.format(
                            "Type check error (size is 0x%x instead of 0x%x)",
                            size, this.size));
        }

        /**
         * Write a type descriptor into the given ByteBuffer.
         */
        public void put(ByteBuffer buf, int num, boolean inl, boolean dealloc) {
            int header = this.header;

            if(inl)
                header |= BIT_INLINE;
            if(dealloc)
                header |= BIT_DEALLOCATE;
            if(!longform)
                header |= (num & 0x0fff) << 16;

            /* Align to the next word boundary. FIXME: hardcoded. */
            while(buf.position() % 4 != 0)
                buf.put((byte) 0);

            buf.putInt(header);
            if(longform) {
                buf.putShort((short) name);
                buf.putShort((short) size);
                buf.putInt(num);
            }
        }

        /* Convenience versions. */
        public void put(ByteBuffer buf, int num) {
            put(buf, num, true, false);
        }
        public void put(ByteBuffer buf) {
            put(buf, 1);
        }

        /**
         * Check the type descriptor read from the given ByteBuffer.
         *
         * @param buf The buffer to read the type descriptor from.
         * @return The number of elements.
         *
         * If the type checking fails, an exception is thrown. As for any
         * other exception, when that occurs the buffer position is
         * unspecified.
         *
         * TODO: recognize out-of-line data.
         */
        public int get(ByteBuffer buf) throws TypeCheckException {
            int header = buf.getInt();
            int number;

            /* Align to the next word boundary. FIXME: hardcoded. */
            while(buf.position() % 4 != 0)
                buf.get();

            checkHeader(header);
            if(longform) {
                int name = buf.getShort();
                int size = buf.getShort();
                checkLongHeader(name, size);
                number = buf.getInt();
            } else {
                number = (header >> 16) & 0x0fff;
            }
            return number;
        }

        /**
         * Check the type descriptor read from the given ByteBuffer.
         *
         * This version behaves the same was as {@link #get(ByteBuffer)}
         * does, but additionally checks the type descriptor's
         * {@code msgt_number} field instead of returning it.
         */
        public void get(ByteBuffer buf, int expectedNumber)
            throws TypeCheckException
        {
            int actualNumber = get(buf);

            if(actualNumber != expectedNumber)
                throw new TypeCheckException(String.format(
                    "Type check error: msgt_number was %d (expected %d)",
                    actualNumber, expectedNumber));
        }
    }

    /**
     * Construct a value for the msgh_bits header field.
     */
    private static int MSGH_BITS(int remote, int local) {
        return remote | (local << 8);
    }
    private static int MSGH_BITS_REMOTE(int bits) {
        return bits & 0xff;
    }
    private static int MSGH_BITS_LOCAL(int bits) {
        return (bits >> 8) & 0xff;
    }
    private static final int MSGH_BITS_COMPLEX = 0x80000000;

    /**
     * The (direct) ByteBuffer backing this message.
     */
    private ByteBuffer buf;

    /**
     * Manage port names stored in the header.
     */
    private class HeaderPort {
        /**
         * Index into {@link MachPort#buf} for the port name we manage.
         */
        private int index;

        /**
         * MachPort object for the port name we manage. If non-null, we hold
         * one external reference to the name. The object is either provided
         * by the user or created on their behalf, and it is always their
         * responsability to {@link MachPort#deallocate} it.
         */
        private MachPort port;

        /** Initialize for a given index in {@link MachMsg#buf}. */
        public HeaderPort(int index) {
            this.index = index;
            port = null;
        }

        /**
         * Prepare to read from the buffer.
         *
         * We assume old port value was modified behind our back, and was
         * deallocated as required by the associated type descriptor.
         */
        public void flip() throws Unsafe { 
            if(port != null) {
                port.releaseName();
                port = null;
            }
        }

        /** Set to MACH_PORT_NULL. */
        public void clear() {
            try {
                /* A port name which was never accessed must be deallocated. */
                if(port == null) {
                    int name = buf.getInt(index);
                    if(name != Mach.Port.NULL)
                        new MachPort(name).deallocate();
                }
                buf.putInt(index, Mach.Port.NULL);
                flip();
            } catch(Unsafe exc) {}
        }

        public void set(MachPort newPort, Type type) {
            if(!type.isPort())
                throw new IllegalArgumentException();

            /* Release any port name we have. */
            clear();
            if(newPort == MachPort.NULL)
                return;

            try {
                if(type.isDeallocatedPort()) {
                    /* The name will be deallocated, so clear newPort. */
                    buf.putInt(index, newPort.clear());
                    port = null;
                } else {
                    /* Otherwise, acquire an external name reference. */
                    buf.putInt(index, newPort.name());
                    port = newPort;
                }
            } catch(Unsafe exc) {}
        }

        public MachPort get() {
            if(port == null) {
                int name = buf.getInt(index);
                if(name != Mach.Port.NULL) {
                    /* Create a new MachPort object and indicate that we
                     * hold an external reference to the port name. */
                    try {
                        port = new MachPort(name);
                        port.name();
                    } catch(Unsafe exc) {}
                }
            }
            return port;
        }
    }

    private HeaderPort remotePort, localPort;
    private Type remoteType, localType;
    private boolean complex;

    /* Extra ports referenced by this message. */
    private Collection<MachPort> refPorts;

    /**
     * Allocate a new message buffer.
     */
    public MachMsg(int size) {
        buf = ByteBuffer.allocateDirect(size);
        buf.order(ByteOrder.nativeOrder());
        remotePort = new HeaderPort(8);
        localPort = new HeaderPort(12);
        refPorts = new ArrayList<MachPort>();
        clear();
    }

    @SuppressWarnings("unused")
    public ByteBuffer buf() throws Unsafe {
        return buf;
    }

    /**
     * Release all port name references held by this message.
     *
     * This operation correponds to a partial {@link #clear()} which does
     * not alter the actual message contents. It is used after a message has
     * been received to release overwritten port references.
     */
    private void releaseNames() throws Unsafe {
        for(MachPort port : refPorts)
            port.releaseName();

        refPorts.clear();
    }

    /**
     * Clear the message's contents.
     */
    public synchronized MachMsg clear() {
        /* Clear the header. */
        remotePort.clear();
        remoteType = null;
        localPort.clear();
        localType = null;
        complex = false;

        /* FIXME: if a received message was not read completely, we leak the
         * remaining port rights and out-of-line memory. */

        /* Release port name references. */
        try { releaseNames(); } catch(Unsafe exc) {}

        /* Reset the message to a blank header. */
        buf.clear();
        for(int i = 0; i < 6; i++)
            buf.putInt(0);

        return this;
    }

    /**
     * 
     */
    public synchronized void flip() throws Unsafe {
        /* Flip header ports. */
        remotePort.flip();
        localPort.flip();

        /* Release port name references. */
        releaseNames();

        /* Read the new values */
        buf.clear();
        buf.limit(buf.getInt(4));
        buf.position(24);
    }

    /** Rewrite the header's {@code msgh_bits} field. */
    private void putBits() {
        int remoteBits = (remoteType != null) ? remoteType.value() : 0;
        int localBits = (localType != null) ? localType.value() : 0;
        int complexBit = complex ? MSGH_BITS_COMPLEX : 0;
        buf.putInt(0, MSGH_BITS(remoteBits, localBits) | complexBit);
    }

    /** Set the header's {@code msgh_remote_port} field. */
    public synchronized MachMsg setRemotePort(MachPort port, Type type) {
        remotePort.set(port, type);
        remoteType = type;
        putBits();
        return this;
    }

    /**
     * Get the header's {@code msgh_remote_port} field.
     *
     * The remote port is type checked against the given type, which should
     * be {@link MachMsg.Type.PORT_RECEIVE}, {@link MachMsg.Type.PORT_SEND}
     * or {@link MachMsg.Type.PORT_SEND_ONCE}. If the types match, the port
     * name is encapsulated into a new {@link MachPort} object and returned.
     * Subsequent calls will return the same {@link MachPort} object.
     */
    public synchronized MachPort getRemotePort(Type type)
        throws TypeCheckException
    {
        int typeVal = MSGH_BITS_REMOTE(buf.getInt(0));
        if(typeVal != type.value())
            throw new TypeCheckException();

        return remotePort.get();
    }

    /** Set the header's {@code msgh_local_port} field. */
    public synchronized MachMsg setLocalPort(MachPort port, Type type) {
        localPort.set(port, type);
        localType = type;
        putBits();
        return this;
    }

    /**
     * Get the header's {@code msgh_local_port} field.
     *
     * The local port is type checked against the given type, which should
     * be {@link MachMsg.Type.PORT_RECEIVE}, {@link MachMsg.Type.PORT_SEND}
     * or {@link MachMsg.Type.PORT_SEND_ONCE}. If the types match, the port
     * name is encapsulated into a new {@link MachPort} object and returned.
     * Subsequent calls will return the same {@link MachPort} object.
     */
    public synchronized MachPort getLocalPort(Type type)
        throws TypeCheckException
    {
        int typeVal = MSGH_BITS_LOCAL(buf.getInt(0));
        if(typeVal != type.value())
            throw new TypeCheckException();

        return localPort.get();
    }

    /** Set the header's {@code msgh_id} field. */
    public synchronized MachMsg setId(int id) {
        buf.putInt(20, id);
        return this;
    }

    /** Get the header's {@code msgh_id} field. */
    public synchronized int getId() {
        return buf.getInt(20);
    }


    /* Writing data items */

    static private interface PutOperation {
        void operate();
    }

    private void atomicPut(PutOperation op) {
        buf.mark();
        try {
            op.operate();
        } catch(Error exc) {
            buf.reset();
            throw exc;
        } catch(RuntimeException exc) {
            buf.reset();
            throw exc;
        }
    }

    /**
     * Append a {@code MACH_MSG_TYPE_CHAR} data item to this message.
     */
    public synchronized MachMsg putChar(final byte ch) {
        atomicPut(new PutOperation() {
            public void operate() {
                Type.CHAR.put(buf);
                buf.put(ch);
            }
        });
        return this;
    }
    public synchronized MachMsg putChar(final byte[] src) {
        atomicPut(new PutOperation() {
            public void operate() {
                Type.CHAR.put(buf, src.length);
                buf.put(src);
            }
        });
        return this;
    }

    /**
     * Append a {@code MACH_MSG_TYPE_INTEGER_64} data item to this message.
     */
    public synchronized MachMsg putInteger64(final long value) {
        atomicPut(new PutOperation() {
            public void operate() {
                Type.INTEGER_64.put(buf);
                buf.putLong(value);
            }
        });
        return this;
    }

    /* Reading data items */

    private static interface GetOperation {
        void operate() throws TypeCheckException;
    }

    private void atomicGet(GetOperation op) throws TypeCheckException {
        buf.mark();
        try {
            op.operate();
        } catch(Error exc) {
            buf.reset();
            throw exc;
        } catch(RuntimeException exc) {
            buf.reset();
            throw exc;
        } catch(TypeCheckException exc) {
            buf.reset();
            throw exc;
        }
    }

    /**
     * Read a {@code MACH_MSG_TYPE_INTEGER_32} data item from this message.
     */
    public synchronized int getInteger32() throws TypeCheckException {
        final int[] value = new int[1];
        atomicGet(new GetOperation() {
            public void operate() throws TypeCheckException {
                Type.INTEGER_32.get(buf, 1);
                value[0] = buf.getInt();
            }
        });
        return value[0];
    }
}
