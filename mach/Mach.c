#include <assert.h>
#include <mach.h>
#include "Mach.h"
#include "Mach$Port.h"

JNIEXPORT jint JNICALL
Java_org_gnu_mach_Mach_replyPort (JNIEnv *env, jclass cls)
{
    return mach_reply_port();
}

JNIEXPORT jint JNICALL
Java_org_gnu_mach_Mach_msg (JNIEnv *env, jclass cls, jobject msg, jint option,
        jint rcvName, jlong timeout, jint notify)
{
    static jmethodID mid_position = NULL;
    if(mid_position == NULL) {
        jclass cls = (*env)->GetObjectClass(env, msg);
        mid_position = (*env)->GetMethodID(env, cls, "position", "()I");
    }

    void *msgAddr = (*env)->GetDirectBufferAddress(env, msg);
    jlong msgSize = (*env)->GetDirectBufferCapacity(env, msg);
    jint msgPos = (*env)->CallIntMethod(env, msg, mid_position);
    assert(msgAddr); /* XXX exception. */

    return mach_msg(msgAddr, option, msgPos, msgSize, rcvName, timeout, notify);
}

JNIEXPORT jint JNICALL
Java_org_gnu_mach_Mach_taskSelf (JNIEnv *env, jclass cls)
{
    return mach_task_self();
}

JNIEXPORT jint JNICALL
Java_org_gnu_mach_Mach_00024Port_allocate (JNIEnv *env, jclass cls, jint task, jint right)
{
    mach_port_t name;
    kern_return_t err;

    err = mach_port_allocate(task, right, &name);
    assert(err == KERN_SUCCESS);

    return name;
}

JNIEXPORT jint JNICALL
Java_org_gnu_mach_Mach_00024Port_deallocate (JNIEnv *env, jclass cls, jint task, jint name)
{
    return mach_port_deallocate(task, name);
}

