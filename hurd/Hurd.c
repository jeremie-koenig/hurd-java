#include <hurd.h>
#include "Hurd.h"

JNIEXPORT jint JNICALL
Java_org_gnu_hurd_Hurd_unsafeGetdport(JNIEnv *env, jobject obj, jint fd)
{
    return getdport(fd);
}
