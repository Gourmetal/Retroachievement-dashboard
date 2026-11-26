#define DISCORDPP_IMPLEMENTATION
#include "discordpp.h"

#include <jni.h>
#include <android/log.h>
#include <memory>
#include <string>
#include <csignal>
#include <exception>
#include <unistd.h>

namespace {
constexpr const char* kTag = "RetroAlertsDiscord";
constexpr uint64_t kApplicationId = 1574859797902266368ULL;
std::unique_ptr<discordpp::Client> gClient;

// Log a short marker, then let Android's debuggerd handle the fatal signal.
// debuggerd writes the full native tombstone, including registers and backtrace.
void nativeCrashSignalHandler(int signalNumber, siginfo_t*, void*) {
    const char* message = "RetroAlertsDiscord: fatal native signal; Android tombstone contains registers and backtrace\\n";
    (void)write(STDERR_FILENO, message, 98);
    (void)kill(getpid(), signalNumber);
}

void nativeTerminateHandler() {
    const char* message = "RetroAlertsDiscord: C++ std::terminate called; aborting for native tombstone\\n";
    (void)write(STDERR_FILENO, message, 78);
    abort();
}

void installNativeCrashDiagnostics() {
    struct sigaction action {};
    action.sa_sigaction = nativeCrashSignalHandler;
    sigemptyset(&action.sa_mask);
    action.sa_flags = SA_SIGINFO | SA_RESETHAND;
    const int signals[] = {SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE};
    for (int signalNumber : signals) {
        sigaction(signalNumber, &action, nullptr);
    }
    std::set_terminate(nativeTerminateHandler);
    __android_log_print(ANDROID_LOG_INFO, kTag,
        "Native crash diagnostics installed (SIGSEGV/SIGABRT/SIGBUS/SIGILL/SIGFPE)");
}

std::string fromJava(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_gourmetal_retroalerts_MainActivity_nativeInitialize(JNIEnv*, jobject, jlong applicationId) {
    installNativeCrashDiagnostics();
    __android_log_print(ANDROID_LOG_INFO, kTag, "nativeInitialize entered; appId=%lld",
        static_cast<long long>(applicationId));
    if (!gClient) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "Stage 1: creating SDK Client");
        gClient = std::make_unique<discordpp::Client>();
        __android_log_print(ANDROID_LOG_INFO, kTag, "Stage 1 passed: SDK Client constructed");
        // Diagnostic build: intentionally defer SetApplicationId until the
        // constructor has been verified independently on the Android device.
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gourmetal_retroalerts_MainActivity_nativeConnect(JNIEnv*, jobject) {
    // Android Rich Presence uses the SDK's unauthenticated RPC transport.
    // Do not call Connect(): that starts the authenticated gateway flow.
}

extern "C" JNIEXPORT void JNICALL
Java_com_gourmetal_retroalerts_MainActivity_nativeUpdatePresence(
    JNIEnv* env, jobject, jstring details, jstring state) {
    if (!gClient) return;
    discordpp::Activity activity{};
    const std::string detailsText = fromJava(env, details);
    const std::string stateText = fromJava(env, state);
    if (!detailsText.empty()) activity.SetDetails(detailsText);
    if (!stateText.empty()) activity.SetState(stateText);
    gClient->UpdateRichPresence(std::move(activity), [](discordpp::ClientResult result) {
        __android_log_print(ANDROID_LOG_INFO, kTag,
            "Rich Presence update completed: %s",
            result.Successful() ? "success" : "failure");
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_gourmetal_retroalerts_MainActivity_nativeRunCallbacks(JNIEnv*, jobject) {
    if (gClient) discordpp::RunCallbacks();
}

extern "C" JNIEXPORT void JNICALL
Java_com_gourmetal_retroalerts_MainActivity_nativeDisconnect(JNIEnv*, jobject) {
    if (gClient) {
        gClient->Disconnect();
    }
}
