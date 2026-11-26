#define DISCORDPP_IMPLEMENTATION
#include "discordpp.h"

#include <jni.h>
#include <android/log.h>
#include <memory>
#include <string>
#include <csignal>
#include <exception>
#include <stdexcept>
#include <cstdlib>
#include <unistd.h>
#include <utility>

namespace {
constexpr const char* kTag = "RetroAlertsDiscord";
std::unique_ptr<discordpp::Client> gClient;

void throwJavaException(JNIEnv* env, const std::string& message) {
    if (env == nullptr || env->ExceptionCheck()) return;
    jclass exceptionClass = env->FindClass("java/lang/IllegalStateException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message.c_str());
        env->DeleteLocalRef(exceptionClass);
    }
}

void nativeCrashSignalHandler(int signalNumber, siginfo_t*, void*) {
    static constexpr char message[] =
        "RetroAlertsDiscord: fatal native signal; Android tombstone contains registers and backtrace\n";
    (void)write(STDERR_FILENO, message, sizeof(message) - 1);
    (void)kill(getpid(), signalNumber);
}

void nativeTerminateHandler() {
    static constexpr char message[] =
        "RetroAlertsDiscord: C++ std::terminate called; aborting for native tombstone\n";
    (void)write(STDERR_FILENO, message, sizeof(message) - 1);
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

void recordJavaCheckpoint(JNIEnv* env, jobject activity, const char* stage) {
    if (env == nullptr || activity == nullptr || env->ExceptionCheck()) return;
    jclass activityClass = env->GetObjectClass(activity);
    if (activityClass == nullptr) return;
    jmethodID method = env->GetMethodID(
        activityClass, "recordDiscordCheckpoint", "(Ljava/lang/String;)V");
    if (method == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(activityClass);
        return;
    }
    jstring message = env->NewStringUTF(stage);
    if (message != nullptr) {
        env->CallVoidMethod(activity, method, message);
        env->DeleteLocalRef(message);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(activityClass);
}

std::string fromJava(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::string exceptionMessage(const char* stage, const char* what) {
    return std::string(stage) + ": " + (what != nullptr ? what : "unknown C++ exception");
}

std::string gPendingDetails;
std::string gPendingState;
bool gPresencePending = false;

void publishPendingPresence() {
    if (!gClient || !gPresencePending ||
        gClient->GetStatus() != discordpp::Client::Status::Ready) {
        return;
    }

    discordpp::Activity presence{};
    presence.SetType(discordpp::ActivityTypes::Playing);
    if (!gPendingDetails.empty()) presence.SetDetails(gPendingDetails);
    if (!gPendingState.empty()) presence.SetState(gPendingState);

    gPresencePending = false;
    __android_log_print(ANDROID_LOG_INFO, kTag,
        "Discord SDK is READY; submitting queued Rich Presence");
    gClient->UpdateRichPresence(std::move(presence), [](discordpp::ClientResult result) {
        __android_log_print(ANDROID_LOG_INFO, kTag,
            "Rich Presence update completed: %s (%s)",
            result.Successful() ? "success" : "failure", result.ToString().c_str());
    });
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
Java_com_gourmetal_retroalerts_MainActivity_nativeInitialize(JNIEnv* env, jobject activity, jlong applicationId) {
    installNativeCrashDiagnostics();
    recordJavaCheckpoint(env, activity, "NATIVE: nativeInitialize entered");
    try {
        if (applicationId <= 0) {
            throw std::invalid_argument("Discord Application ID must be positive");
        }
        __android_log_print(ANDROID_LOG_INFO, kTag, "nativeInitialize entered; appId=%lld",
            static_cast<long long>(applicationId));
        if (!gClient) {
            recordJavaCheckpoint(env, activity, "NATIVE: entering discordpp::Client constructor");
            __android_log_print(ANDROID_LOG_INFO, kTag, "Stage 1: creating SDK Client");
            auto client = std::make_unique<discordpp::Client>();
            recordJavaCheckpoint(env, activity, "NATIVE: discordpp::Client constructor returned");
            __android_log_print(ANDROID_LOG_INFO, kTag, "Stage 2: setting Discord application ID");
            client->SetApplicationId(static_cast<uint64_t>(applicationId));
            recordJavaCheckpoint(env, activity, "NATIVE: Client::SetApplicationId returned");
            client->SetStatusChangedCallback([](
                discordpp::Client::Status status,
                discordpp::Client::Error error,
                int32_t errorDetail) {
                __android_log_print(ANDROID_LOG_INFO, kTag,
                    "Discord SDK status changed: status=%d error=%d detail=%d",
                    static_cast<int>(status), static_cast<int>(error), errorDetail);
                if (status == discordpp::Client::Status::Ready) {
                    publishPendingPresence();
                }
            });
            recordJavaCheckpoint(env, activity, "NATIVE: Discord READY callback registered");
            gClient = std::move(client);
            recordJavaCheckpoint(env, activity, "NATIVE: Discord client created and configured");
        } else {
            recordJavaCheckpoint(env, activity, "NATIVE: existing SDK Client reused");
        }
    } catch (const std::exception& error) {
        const auto message = exceptionMessage("nativeInitialize failed", error.what());
        recordJavaCheckpoint(env, activity, message.c_str());
        __android_log_print(ANDROID_LOG_ERROR, kTag, "%s", message.c_str());
        throwJavaException(env, message);
    } catch (...) {
        const std::string message = "nativeInitialize failed: unknown C++ exception";
        recordJavaCheckpoint(env, activity, message.c_str());
        __android_log_print(ANDROID_LOG_ERROR, kTag, "%s", message.c_str());
        throwJavaException(env, message);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gourmetal_retroalerts_MainActivity_nativeConnect(JNIEnv* env, jobject activity) {
    try {
        if (!gClient) throw std::runtime_error("Discord SDK client has not been initialized");
        recordJavaCheckpoint(env, activity, "NATIVE: starting Discord RPC connection");
        gClient->Connect();
        __android_log_print(ANDROID_LOG_INFO, kTag,
            "Discord SDK Connect() invoked; waiting for Status::Ready");
    } catch (const std::exception& error) {
        const auto message = exceptionMessage("nativeConnect failed", error.what());
        recordJavaCheckpoint(env, activity, message.c_str());
        __android_log_print(ANDROID_LOG_ERROR, kTag, "%s", message.c_str());
        throwJavaException(env, message);
    } catch (...) {
        throwJavaException(env, "nativeConnect failed: unknown C++ exception");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gourmetal_retroalerts_MainActivity_nativeUpdatePresence(
    JNIEnv* env, jobject activity, jstring details, jstring state) {
    try {
        if (!gClient) throw std::runtime_error("Discord SDK client has not been initialized");
        gPendingDetails = fromJava(env, details);
        gPendingState = fromJava(env, state);
        gPresencePending = true;
        recordJavaCheckpoint(env, activity, "NATIVE: presence queued; waiting for Discord READY");
        publishPendingPresence();
    } catch (const std::exception& error) {
        const auto message = exceptionMessage("nativeUpdatePresence failed", error.what());
        recordJavaCheckpoint(env, activity, message.c_str());
        __android_log_print(ANDROID_LOG_ERROR, kTag, "%s", message.c_str());
        throwJavaException(env, message);
    } catch (...) {
        const std::string message = "nativeUpdatePresence failed: unknown C++ exception";
        recordJavaCheckpoint(env, activity, message.c_str());
        throwJavaException(env, message);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gourmetal_retroalerts_MainActivity_nativeRunCallbacks(JNIEnv* env, jobject activity) {
    try {
        if (gClient) discordpp::RunCallbacks();
    } catch (const std::exception& error) {
        const auto message = exceptionMessage("Discord callback pump failed", error.what());
        recordJavaCheckpoint(env, activity, message.c_str());
        throwJavaException(env, message);
    } catch (...) {
        throwJavaException(env, "Discord callback pump failed: unknown C++ exception");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gourmetal_retroalerts_MainActivity_nativeDisconnect(JNIEnv* env, jobject activity) {
    try {
        if (gClient) {
            gPresencePending = false;
            gPendingDetails.clear();
            gPendingState.clear();
            gClient->Disconnect();
            gClient.reset();
        }
    } catch (const std::exception& error) {
        const auto message = exceptionMessage("Discord disconnect failed", error.what());
        recordJavaCheckpoint(env, activity, message.c_str());
        throwJavaException(env, message);
    } catch (...) {
        throwJavaException(env, "Discord disconnect failed: unknown C++ exception");
    }
}
