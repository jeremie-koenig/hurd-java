import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import org.gnu.mach.Mach;
import org.gnu.mach.MachPort;
import org.gnu.mach.MachMsg;
import org.gnu.hurd.Hurd;

public class HelloMach {
    private static void hello(MachPort stdout) throws Exception {
        MachMsg msg = new MachMsg(1000);
        MachPort reply = Mach.replyPort();

        /* mach_msg_header_t */
        msg.setRemotePort(stdout, MachMsg.Type.COPY_SEND);
        msg.setLocalPort(reply, MachMsg.Type.MAKE_SEND_ONCE);
        msg.setId(21000);

        /* Data */
        byte data[] = "Hello in Java!\n".getBytes();
        MachMsg.Type.CHAR.put(msg.buf, data.length);
        msg.buf.put(data);

        /* Offset */
        MachMsg.Type.INTEGER_64.put(msg.buf);
        msg.buf.putLong(-1);

        int err = Mach.msg(msg.buf, Mach.SEND_MSG | Mach.RCV_MSG, reply, Mach.MSG_TIMEOUT_NONE, null);
        System.out.println("err = " + err);
        msg.buf.position(msg.buf.getInt(4)).flip().position(24);

        MachMsg.Type.INTEGER_32.get(msg.buf);
        int retcode = msg.buf.getInt();
        System.out.println("retcode = " + retcode);

        MachMsg.Type.INTEGER_32.get(msg.buf);
        int amount = msg.buf.getInt();
        System.out.println("amount = " + amount);

        reply.deallocate();
        msg.clear();
    }

    public static void testHello() throws Exception {
        Hurd hurd = new Hurd();
        MachPort stdoutp = hurd.getdport(1);
        hello(stdoutp);
        stdoutp.deallocate();
    }

    public static void main(String argv[]) throws Exception {
        System.loadLibrary("hurd-java");

        testHello();

        for(int i = 0; i < 100; i++) {
            byte[] foo = new byte[10000000];
            System.gc();
        }
    }
}
