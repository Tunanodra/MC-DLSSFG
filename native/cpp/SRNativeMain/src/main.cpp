#include <string>
#include "define.h"
#include "utils.h"
#include "com_dgtdi_mcdlssg_core_MCDLSSGNative.h"
#include "nvg/all.h"

JNIEXPORT jstring JNICALL Java_com_dgtdi_mcdlssg_core_MCDLSSGNative_getVersionInfo(JNIEnv *env, jclass) {
    return (env)->NewStringUTF(SRLIB_VERSION);
}

JNIEXPORT void JNICALL Java_com_dgtdi_mcdlssg_core_MCDLSSGNative_freeDirectBuffer(
    JNIEnv *env, jclass, jobject buffer) {
    void *ptr = env->GetDirectBufferAddress(buffer);
    if (ptr)
        free(ptr);
}