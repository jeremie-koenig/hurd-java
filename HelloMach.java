import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import org.gnu.mach.Mach;
import org.gnu.mach.MachPort;
import org.gnu.hurd.Hurd;

public class HelloMach {
    private static final short TYPE_CHAR = 8;
    //private static final int TYPE_INTEGER_64 = 11;
    private static final short TYPE_COPY_SEND = 19;
    private static final short TYPE_MAKE_SEND_ONCE = 21;

    private static final int MSGH_BITS(int remote, int local) {
        return remote | (local << 8);
    }

    private static void hello(MachPort stdout) {
        ByteBuffer msg = ByteBuffer.allocateDirect(1000);
        MachPort reply = MachPort.allocate();

        msg.order(ByteOrder.nativeOrder());

        /* mach_msg_header_t */
        msg.putInt(MSGH_BITS(TYPE_COPY_SEND, TYPE_MAKE_SEND_ONCE));
        msg.putInt(0);                  /* msgh_size */
        msg.putInt(stdout.name());      /* msgh_remote_port */
        msg.putInt(reply.name());       /* msgh_local_port */
        msg.putInt(0);                  /* msgh_seqno */
        msg.putInt(21000);              /* msgh_id */

        /* Data */
        byte data[] = "Hello in Java!_\n".getBytes();
        msg.putInt(0x30000000);         /* msgtl_header: inline, longform */
        msg.putShort(TYPE_CHAR);        /* msgtl_name */
        msg.putShort((short) 8);        /* msgtl_size */
        msg.putInt(data.length);        /* msgtl_number */
        msg.put(data);

        /* Offset */
        msg.putInt(0x1001400b);
        msg.putLong(-1);

        int err = Mach.msg(msg, Mach.SEND_MSG | Mach.RCV_MSG, reply, Mach.MSG_TIMEOUT_NONE, null);
        System.out.println("err = " + err);

        reply.deallocate();
    }

    public static void main(String argv[]) {
        Hurd hurd = new Hurd();
        System.loadLibrary("hurd-java");
        hello(hurd.getdport(1));
    }
}
