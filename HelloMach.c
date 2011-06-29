#include <stdio.h>
#include <string.h>
#include <mach-java.h>
#include <hurd.h>
#include "HelloMach.h"

JNIEXPORT void JNICALL Java_HelloMach_hello (JNIEnv *env, jclass cls, jobject port)
{
    static const char *hello = "Hello, World!\n";
    mach_port_t stdoutp;
    mach_port_t replyp;
    mach_msg_return_t err;

    union {
        struct {
            mach_msg_header_t hdr;
            mach_msg_type_long_t dataType;
            char data[14];
            mach_msg_type_t offsetType;
            loff_t offset;
        } req;
        struct {
            mach_msg_header_t hdr;
            mach_msg_type_t RetCodeType;
            kern_return_t RetCode;
            mach_msg_type_t amountType;
            vm_size_t amount;
            char unused[100];
        } rep;
    } msg;

    stdoutp = mach_java_getport(env, port);
    mach_port_allocate(mach_task_self(), MACH_PORT_RIGHT_RECEIVE, &replyp);

    msg.req.hdr.msgh_bits =
        MACH_MSGH_BITS(MACH_MSG_TYPE_COPY_SEND, MACH_MSG_TYPE_MAKE_SEND_ONCE);
    msg.req.hdr.msgh_remote_port = stdoutp;
    msg.req.hdr.msgh_local_port = replyp;
    msg.req.hdr.msgh_id = 21000;

    msg.req.dataType.msgtl_header.msgt_name = 0;
    msg.req.dataType.msgtl_header.msgt_size = 0;
    msg.req.dataType.msgtl_header.msgt_number = 0;
    msg.req.dataType.msgtl_header.msgt_inline = TRUE;
    msg.req.dataType.msgtl_header.msgt_longform = TRUE;
    msg.req.dataType.msgtl_header.msgt_deallocate = FALSE;
    msg.req.dataType.msgtl_header.msgt_unused = 0;
    msg.req.dataType.msgtl_name = MACH_MSG_TYPE_CHAR;
    msg.req.dataType.msgtl_size = 8;
    msg.req.dataType.msgtl_number = sizeof msg.req.data;
    memcpy(msg.req.data, hello, sizeof msg.req.data);

    msg.req.offsetType.msgt_name = MACH_MSG_TYPE_INTEGER_64;
    msg.req.offsetType.msgt_size = 64;
    msg.req.offsetType.msgt_number = 1;
    msg.req.offsetType.msgt_inline = TRUE;
    msg.req.offsetType.msgt_longform = FALSE;
    msg.req.offsetType.msgt_deallocate = FALSE;
    msg.req.offsetType.msgt_unused = 0;
    msg.req.offset = -1;

    err = mach_msg(
            (mach_msg_header_t *) &msg,
            MACH_SEND_MSG | MACH_RCV_MSG,
            sizeof msg.req,
            sizeof msg.rep,
            replyp,
            MACH_MSG_TIMEOUT_NONE,
            MACH_PORT_NULL);

    printf("err = %d, rc = %d, amt = %d\n",
            err, msg.rep.RetCode, msg.rep.amount);

    mach_port_deallocate(mach_task_self(), replyp);
}
