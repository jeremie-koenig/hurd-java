import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import org.gnu.mach.Mach;
import org.gnu.mach.MachPort;
import org.gnu.mach.MachMsg;
import org.gnu.hurd.Hurd;

public class HelloMach {
    private static final short TYPE_COPY_SEND = 19;
    private static final short TYPE_MAKE_SEND_ONCE = 21;

    private static final int MSGH_BITS(int remote, int local) {
        return remote | (local << 8);
    }

    private static void hello(MachPort stdout) throws Exception {
        ByteBuffer msg = ByteBuffer.allocateDirect(1000);
        MachPort reply = Mach.replyPort();

        msg.order(ByteOrder.nativeOrder());

        /* mach_msg_header_t */
        msg.putInt(MSGH_BITS(TYPE_COPY_SEND, TYPE_MAKE_SEND_ONCE));
        msg.putInt(0);                  /* msgh_size */
        msg.putInt(stdout.name());      /* msgh_remote_port */
        msg.putInt(reply.name());       /* msgh_local_port */
        msg.putInt(0);                  /* msgh_seqno */
        msg.putInt(21000);              /* msgh_id */

        /* Data */
        byte data[] = "Hello in Java!\n".getBytes();
        MachMsg.Type.CHAR.put(msg, data.length);
        msg.put(data);

        /* Offset */
        MachMsg.Type.INTEGER_64.put(msg);
        msg.putLong(-1);

        int err = Mach.msg(msg, Mach.SEND_MSG | Mach.RCV_MSG, reply, Mach.MSG_TIMEOUT_NONE, null);
        System.out.println("err = " + err);
        msg.position(msg.getInt(4)).flip().position(24);

        MachMsg.Type.INTEGER_32.get(msg);
        int retcode = msg.getInt();
        System.out.println("retcode = " + retcode);

        MachMsg.Type.INTEGER_32.get(msg);
        int amount = msg.getInt();
        System.out.println("amount = " + amount);

        reply.deallocate();
    }

    public static void main(String argv[]) throws Exception {
        Hurd hurd = new Hurd();
        System.loadLibrary("hurd-java");
        hello(hurd.getdport(1));
    }
}
