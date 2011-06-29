#include <assert.h>
#include <mach.h>
#include <mach-java.h>
#include "MachPort.h"

static jfieldID nameID;
static jmethodID ctorID;
static jclass cls_MachPort;

JNIEXPORT void JNICALL
Java_org_gnu_mach_MachPort_initIDs(JNIEnv *env, jclass cls)
{
    cls_MachPort = cls;
    nameID = (*env)->GetFieldID(env, cls_MachPort, "name", "I");
    ctorID = (*env)->GetMethodID(env, cls_MachPort, "<init>", "(I)V");
    assert(nameID != NULL);
}

JNIEXPORT void JNICALL
Java_org_gnu_mach_MachPort_nativeDeallocate (JNIEnv *env, jobject obj)
{
    mach_port_deallocate(mach_task_self(), mach_java_getport(env, obj));
}

jobject mach_java_makeport(JNIEnv *env, mach_port_t name)
{
    if(!cls_MachPort) {
        /* Ensures initIDs() has been called. */
        jclass cls = (*env)->FindClass(env, "org/gnu/mach/MachPort");
        (*env)->DeleteLocalRef(env, cls);
    }
    return (*env)->NewObject(env, cls_MachPort, ctorID, (jint) name);
}

mach_port_t mach_java_getport(JNIEnv *env, jobject obj)
{
    return (*env)->GetIntField(env, obj, nameID);
}
