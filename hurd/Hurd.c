#include <mach-java.h>
#include <hurd.h>
#include "Hurd.h"

JNIEXPORT jobject JNICALL
Java_org_gnu_hurd_Hurd_getdport(JNIEnv *env, jobject obj, jint fd)
{
    return mach_java_makeport(env, getdport(fd));
}
