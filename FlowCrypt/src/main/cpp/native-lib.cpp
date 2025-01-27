// from https://github.com/janeasystems/nodejs-mobile

#include <jni.h>
#include <string>
#include <cstdlib>
#include "node.h"
#include <pthread.h>
#include <unistd.h>
#include <android/log.h>
#include "rn-bridge.h"

// cache the environment variable for the thread running node to call into java
JNIEnv *cacheEnvPointer = NULL;

// prepend log calls with this
const char *ADBTAG = "NODEJS-MOBILE";

extern "C"
JNIEXPORT void JNICALL
Java_com_flowcrypt_email_node_NativeNode_sendNativeMessageToNode(JNIEnv *env, jobject /* this */, jstring msg) {
    const char *nativeMessage = env->GetStringUTFChars(msg, 0);
    rn_bridge_notify(nativeMessage);
    env->ReleaseStringUTFChars(msg, nativeMessage);
}

// may be useless - just use "return jint(node::Start(argument_count,argv));" at the bottom - evaluate later
extern "C" int callintoNode(int argc, char *argv[]) {
    const int exit_code = node::Start(argc, argv);
    return exit_code;
}

#define APPNAME "RNBRIDGE"

void rcv_message(char *msg) {
    JNIEnv *env = cacheEnvPointer;
    if (!env) return;
    jclass cls2 = env->FindClass("com/flowcrypt/email/node/NativeNode");  // try to find the class
    if (cls2 != nullptr) {
        jmethodID m_sendMessage = env->GetStaticMethodID(cls2, "receiveNativeMessageFromNode",
                                                         "(Ljava/lang/String;)V");  // find method
        if (m_sendMessage != nullptr) {
            jstring java_msg = env->NewStringUTF(msg);
            env->CallStaticVoidMethod(cls2, m_sendMessage, java_msg); // call method
        }
    }
}

// Start threads to redirect stdout and stderr to logcat.
int pipe_stdout[2];
int pipe_stderr[2];
pthread_t thread_stdout;
pthread_t thread_stderr;

void *thread_stderr_func(void *) {
    ssize_t redirect_size;
    char buf[2048];
    while ((redirect_size = read(pipe_stderr[0], buf, sizeof buf - 1)) > 0) {
        //__android_log will add a new line anyway.
        if (buf[redirect_size - 1] == '\n')
            --redirect_size;
        buf[redirect_size] = 0;
        __android_log_write(ANDROID_LOG_ERROR, ADBTAG, buf);
    }
    return 0;
}

void *thread_stdout_func(void *) {
    ssize_t redirect_size;
    char buf[2048];
    while ((redirect_size = read(pipe_stdout[0], buf, sizeof buf - 1)) > 0) {
        //__android_log will add a new line anyway.
        if (buf[redirect_size - 1] == '\n')
            --redirect_size;
        buf[redirect_size] = 0;
        __android_log_write(ANDROID_LOG_INFO, ADBTAG, buf);
    }
    return 0;
}

int start_redirecting_stdout_stderr() {
    //set stdout as unbuffered.
    setvbuf(stdout, 0, _IONBF, 0);
    pipe(pipe_stdout);
    dup2(pipe_stdout[1], STDOUT_FILENO);

    //set stderr as unbuffered.
    setvbuf(stderr, 0, _IONBF, 0);
    pipe(pipe_stderr);
    dup2(pipe_stderr[1], STDERR_FILENO);

    if (pthread_create(&thread_stdout, 0, thread_stdout_func, 0) == -1)
        return -1;
    pthread_detach(thread_stdout);

    if (pthread_create(&thread_stderr, 0, thread_stderr_func, 0) == -1)
        return -1;
    pthread_detach(thread_stderr);

    return 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_flowcrypt_email_node_NativeNode_stringFromJNI(JNIEnv *env, jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

//node's libUV requires all arguments being on contiguous memory.
extern "C" jint JNICALL
Java_com_flowcrypt_email_node_NativeNode_startNodeWithArguments(JNIEnv *env, jobject /* this */,
                                                                jobjectArray arguments) {

    //argc
    jsize argument_count = env->GetArrayLength(arguments);

    //Compute byte size need for all arguments in contiguous memory.
    int c_arguments_size = 0;
    for (int i = 0; i < argument_count; i++) {
        c_arguments_size += strlen(env->GetStringUTFChars((jstring) env->GetObjectArrayElement(arguments, i), 0));
        c_arguments_size++; // for '\0'
    }

    //Stores arguments in contiguous memory.
    char *args_buffer = (char *) calloc(c_arguments_size, sizeof(char));

    //argv to pass into node.
    char *argv[argument_count];

    //To iterate through the expected start position of each argument in args_buffer.
    char *current_args_position = args_buffer;

    //Populate the args_buffer and argv.
    for (int i = 0; i < argument_count; i++) {
        const char *current_argument = env->GetStringUTFChars((jstring) env->GetObjectArrayElement(arguments, i), 0);

        //Copy current argument to its expected position in args_buffer
        strncpy(current_args_position, current_argument, strlen(current_argument));

        //Save current argument start position in argv
        argv[i] = current_args_position;

        //Increment to the next argument's expected position.
        current_args_position += strlen(current_args_position) + 1;
    }

    //Start threads to show stdout and stderr in logcat.
    if (start_redirecting_stdout_stderr() == -1) {
        __android_log_write(ANDROID_LOG_ERROR, ADBTAG, "Couldn't start redirecting stdout and stderr to logcat.");
    }

    rn_register_bridge_cb(&rcv_message);

    cacheEnvPointer = env;

    //Start node, with argc and argv.
    return jint(callintoNode(argument_count, argv));
}
