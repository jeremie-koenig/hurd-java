#include <assert.h>
#include <mach-java.h>
#include "Mach.h"

JNIEXPORT jint JNICALL
Java_org_gnu_mach_Mach_msg (JNIEnv *env, jclass cls, jobject msg, jint option,
        jobject rcvName, jlong timeout, jobject notify)
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

    return mach_msg(msgAddr, option, msgPos, msgSize,
                    mach_java_getport(env, rcvName), timeout,
                    mach_java_getport(env, notify));
}
