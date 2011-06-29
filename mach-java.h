/**
 * @file mach-java.h
 * @brief JNI interfaces for the org.gnu.mach package.
 */
#ifndef __MACH_JAVA_H__
#define __MACH_JAVA_H__

#include <mach.h>
#include <jni.h>

/**
 * Construct a MachPort object for the given mach_port_t.
 *
 * This consumes one reference to @p name. The reference will be released
 * when deallocate() is called, or when the new object is collected.
 */ 
jobject mach_java_makeport(JNIEnv *env, mach_port_t name);

/**
 * Retreive a port name from a MachPort object.
 */
mach_port_t mach_java_getport(JNIEnv *env, jobject obj);

#endif
