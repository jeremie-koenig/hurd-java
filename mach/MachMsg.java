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
 * Mach messages store port names in their header and in some of the
 * subsequent data items. To avoid port rights from being accidentally
 * deallocated or leaked, we must handle them carefully.
 *
 * When a {@link MachMsg} object is initially created, or after it has been
 * cleared, the header's port names are initialized to {@code MACH_PORT_NULL}
 * and no correponding {@link MachMsg} object exists. When port names are
 * added to the message contents through the {@link #setRemotePort} and
 * {@link #setLocalPort} methods or through one of the {@code put}
 * operations, on of two things can happen depending on the associated
 * {@link MachMsgType} used.
 *
 * If the port type is {@link MachMsgType#COPY_SEND}, {@link
 * MachMsgType#MAKE_SEND} or {@link MachMsgType#MAKE_SEND_ONCE}), the port
 * name is acquired using {@link MachPort#name()} and is kept as long as the
 * buffer contains the port name in question.
 *
 * If the port type is {@link MachMsgType#MOVE_RECEIVE}, {@link
 * MachMsgType#MOVE_SEND} or {@link MachMsgType#MOVE_SEND_ONCE}, the port
 * name is acquired using {@link MachPort#clear()}.
 */
public class MachMsg {
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
         * We assume the old port value was modified behind our back, and was
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

        /**
         * Write the name encapsulated in {@param newPort} into the buffer. An
         * external reference to the port name will be held until
         * {@link #clear} is called.
         */
        public void set(MachPort newPort, MachMsgType type) {
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

        /**
         * Read the port name from the buffer and return a corresponding
         * MachPort object. An external reference to the port name will be held
         * until {@link #clear} is called.
         */
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
    private MachMsgType remoteType, localType;
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
        int remoteBits = (remoteType != null) ? remoteType.name() : 0;
        int localBits = (localType != null) ? localType.name() : 0;
        int complexBit = complex ? MSGH_BITS_COMPLEX : 0;
        buf.putInt(0, MSGH_BITS(remoteBits, localBits) | complexBit);
    }

    /** Set the header's {@code msgh_remote_port} field. */
    public synchronized MachMsg setRemotePort(MachPort port, MachMsgType type) {
        remotePort.set(port, type);
        remoteType = type;
        putBits();
        return this;
    }

    /**
     * Get the header's {@code msgh_remote_port} field.
     *
     * The remote port is type checked against the given type, which should
     * be {@link MachMsgType#PORT_RECEIVE}, {@link MachMsgType#PORT_SEND}
     * or {@link MachMsgType#PORT_SEND_ONCE}. If the types match, the port
     * name is encapsulated into a new {@link MachPort} object and returned.
     * Subsequent calls will return the same {@link MachPort} object.
     */
    public synchronized MachPort getRemotePort(MachMsgType type)
        throws TypeCheckException
    {
        int typeVal = MSGH_BITS_REMOTE(buf.getInt(0));
        if(typeVal != type.name())
            throw new TypeCheckException();

        return remotePort.get();
    }

    /** Set the header's {@code msgh_local_port} field. */
    public synchronized MachMsg setLocalPort(MachPort port, MachMsgType type) {
        localPort.set(port, type);
        localType = type;
        putBits();
        return this;
    }

    /**
     * Get the header's {@code msgh_local_port} field.
     *
     * The local port is type checked against the given type, which should
     * be {@link MachMsgType#PORT_RECEIVE}, {@link MachMsgType#PORT_SEND}
     * or {@link MachMsgType#PORT_SEND_ONCE}. If the types match, the port
     * name is encapsulated into a new {@link MachPort} object and returned.
     * Subsequent calls will return the same {@link MachPort} object.
     */
    public synchronized MachPort getLocalPort(MachMsgType type)
        throws TypeCheckException
    {
        int typeVal = MSGH_BITS_LOCAL(buf.getInt(0));
        if(typeVal != type.name())
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

    private static interface PutOperation {
        void operate();
    }

    private synchronized MachMsg atomicPut(MachMsgType type, boolean port,
                                           PutOperation op)
        throws TypeCheckException
    {
        buf.mark();
        try {
            if(port != type.isPort())
                throw new TypeCheckException(String.format(
                    "attempt to read %s item as a %s",
                    type.isPort() ? "port" : "non-port",
                    port ? "port" : "non-port"));

            type.put(buf);
            int pos = buf.position();
            op.operate();
            int bytes = buf.position() - pos;

            if(bytes != type.bytes())
                throw new TypeCheckException(String.format(
                    "data item is %d bytes long, type descriptor requires %d",
                    bytes, type.bytes()));

            return this;
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

    /* This mirrors the relative put methods in ByteBuffer. */

    /** Append a single-byte data item to this message. */
    public MachMsg putByte(MachMsgType type, final byte data)
        throws TypeCheckException
    {
        return atomicPut(type, false, new PutOperation() {
            public void operate() { buf.put(data); }
        });
    }

    /** Append a data item to this message as a {@code byte} array. */
    public MachMsg putBytes(MachMsgType type, final byte[] data)
        throws TypeCheckException
    {
        return atomicPut(type, false, new PutOperation() {
            public void operate() { buf.put(data); }
        });
    }

    /** Append a 2-bytes data item to this message as a {@code short} value. */
    public MachMsg putShort(MachMsgType type, final short data)
        throws TypeCheckException
    {
        return atomicPut(type, false, new PutOperation() {
            public void operate() { buf.putShort(data); }
        });
    }

    /** Append a 4-bytes data item to this message as an {@code int} value. */
    public MachMsg putInt(MachMsgType type, final int data)
        throws TypeCheckException
    {
        return atomicPut(type, false, new PutOperation() {
            public void operate() { buf.putInt(data); }
        });
    }

    /** Append an 8-bytes data item to this message as a {@code long} value. */
    public MachMsg putLong(MachMsgType type, final long data)
        throws TypeCheckException
    {
        return atomicPut(type, false, new PutOperation() {
            public void operate() { buf.putLong(data); }
        });
    }

    /* Convenience versions using predefined types */

    /** Append a {@code MACH_MSG_TYPE_CHAR} data item to this message. */
    public MachMsg putByte(final byte data)
    {
        try {
            putByte(MachMsgType.CHAR, data);
        } catch(TypeCheckException exc) {
            assert false;
        }
        return this;
    }

    /** Append a {@code MACH_MSG_TYPE_CHAR} data item to this message. */
    public MachMsg putBytes(final byte[] data)
    {
        MachMsgType type = MachMsgType.CHAR.withNumber(data.length);
        try {
            putBytes(type, data);
        } catch(TypeCheckException exc) {
            assert false;
        }
        return this;
    }

    /** Append a {@code MACH_MSG_TYPE_INTEGER_32} data item to this message. */
    public MachMsg putInt(final int data) {
        try {
            putInt(MachMsgType.INTEGER_32, data);
        } catch(TypeCheckException exc) {
            assert false;
        }
        return this;
    }

    /** Append a {@code MACH_MSG_TYPE_INTEGER_64} data item to this message. */
    public MachMsg putLong(final long data) {
        try {
            putLong(MachMsgType.INTEGER_64, data);
        } catch(TypeCheckException exc) {
            assert false;
        }
        return this;
    }

    /* Reading data items */

    private static interface GetOperation<T> {
        T operate() throws TypeCheckException;
    }

    private synchronized <T> T atomicGet(MachMsgType.Template type, int number,
                                         boolean port, GetOperation<T> op)
        throws TypeCheckException
    {
        buf.mark();
        try {
            if(port != type.isPort())
                throw new TypeCheckException(String.format(
                    "attempt to read %s item as a %s",
                    type.isPort() ? "port" : "non-port",
                    port ? "port" : "non-port"));

            type.check(buf, number);
            int pos = buf.position();
            T data = op.operate();
            int bytes = buf.position() - pos;

            if(bytes != type.bytes())
                throw new TypeCheckException(String.format(
                    "data item is %d bytes long, expected %d",
                    type.bytes(), bytes));

            return data;
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

    private static interface VariableGetOperation<T> {
        T operate(int number) throws TypeCheckException;
    }

    private synchronized <T> T atomicVariableGet(MachMsgType.Template type,
                                   boolean port, VariableGetOperation<T> op)
        throws TypeCheckException
    {
        buf.mark();
        try {
            if(port != type.isPort())
                throw new TypeCheckException(String.format(
                    "attempt to read %s item as a %s",
                    type.isPort() ? "port" : "non-port",
                    port ? "port" : "non-port"));

            int number = type.check(buf);
            int pos = buf.position();
            T data = op.operate(number);
            int bytes = buf.position() - pos;

            if(bytes != type.bytes())
                throw new TypeCheckException(String.format(
                    "data item is %d bytes long, expected %d",
                    type.bytes(), bytes));

            return data;
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

    /** Read a single-byte data item from this message. */
    public byte getByte(MachMsgType.Template type, int number)
        throws TypeCheckException
    {
        return atomicGet(type, number, false, new GetOperation<Byte>() {
            public Byte operate() { return buf.get(); }
        });
    }

    /** Read a fixed-length data item from this message as a byte array. */
    public byte[] getBytes(final MachMsgType.Template type, final int number)
        throws TypeCheckException
    {
        return atomicGet(type, number, false, new GetOperation<byte[]>() {
            public byte[] operate() {
                byte[] data = new byte[type.size() * number / 8];
                buf.get(data);
                return data;
            }
        });
    }

    /** Read a variable-length data item from this message as a byte array. */
    public byte[] getBytes(final MachMsgType.Template type)
        throws TypeCheckException
    {
        return atomicVariableGet(type, false, new VariableGetOperation<byte[]>() {
            public byte[] operate(int number) {
                byte[] data = new byte[type.size() * number / 8];
                buf.get(data);
                return data;
            }
        });
    }

    /** Read a two-byte data item from this message as a {@code short} value. */
    public short getShort(MachMsgType.Template type, int number)
        throws TypeCheckException
    {
        return atomicGet(type, number, false, new GetOperation<Short>() {
            public Short operate() { return buf.getShort(); }
        });
    }

    /** Read a four-bytes data item from this message as an {@code int} value. */
    public int getInt(MachMsgType.Template type, int number)
        throws TypeCheckException
    {
        return atomicGet(type, number, false, new GetOperation<Integer>() {
            public Integer operate() { return buf.getInt(); }
        });
    }

    /** Read an 8-bytes data item from this message as an {@code long} value. */
    public long getLong(MachMsgType.Template type, int number)
        throws TypeCheckException
    {
        return atomicGet(type, number, false, new GetOperation<Long>() {
            public Long operate() { return buf.getLong(); }
        });
    }

    /** Read a port from this message. */
    public MachPort getPort(MachMsgType.Template type)
        throws TypeCheckException
    {
        /* NB: it's important that we read the name first, and only instanciate
         * a MachPort object once we're sure no type check error is going to
         * occur; otherwise we could consume extra Mach user references by
         * attempting to read the same one multiple times. */

        int name = atomicGet(type, 1, true, new GetOperation<Integer>() {
            public Integer operate() { return buf.getInt(); }
        });

        MachPort port = null;
        try { port = new MachPort(name); } catch(Unsafe exc) {}

        return port;
    }

    /** Read a port array from this message. */
    public MachPort[] getPorts(MachMsgType.Template type)
        throws TypeCheckException
    {
        /* NB: the same as above applies. */

        int[] names = atomicVariableGet(type, true,
            new VariableGetOperation<int[]>() {
                public int[] operate(int number) {
                    int[] data = new int[number];
                    for(int i = 0; i < number; i++)
                        data[i] = buf.getInt();
                    return data;
                }
            });

        MachPort[] ports = new MachPort[names.length];
        try { 
            for(int i = 0; i < names.length; i++)
                ports[i] = new MachPort(names[i]);
        } catch(Unsafe exc) {}

        return ports;
    }

    /* Convenience versions using predefined types */

    /** Read a {@code MACH_MSG_TYPE_CHAR} data item from this message. */
    public byte getByte() throws TypeCheckException {
        return getByte(MachMsgType.CHAR, 1);
    }

    /** Read a {@code MACH_MSG_TYPE_CHAR} data item from this message. */
    public byte[] getBytes() throws TypeCheckException {
        return getBytes(MachMsgType.CHAR);
    }

    /** Read a {@code MACH_MSG_TYPE_INTEGER_32} data item from this message. */
    public int getInt() throws TypeCheckException {
        return getInt(MachMsgType.INTEGER_32, 1);
    }

    /** Read a {@code MACH_MSG_TYPE_INTEGER_64} data item from this message. */
    public long getLong() throws TypeCheckException {
        return getLong(MachMsgType.INTEGER_64, 1);
    }
}
