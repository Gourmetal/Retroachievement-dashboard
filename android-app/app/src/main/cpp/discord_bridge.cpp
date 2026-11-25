#define DISCORDPP_IMPLEMENTATION
#include "discordpp.h"

#include <jni.h>
#include <android/log.h>
#include <memory>
#include <string>

namespace {
constexpr const char* kTag = "RetroAlertsDiscord";
constexpr uint64_t kApplicationId = 1574859797902266368ULL;
std::unique_ptr<discordpp::Client> gClient;
bool gReady = false;

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
    if (!gClient) {
        gClient = std::make_unique<discordpp::Client>();
        gClient->SetApplicationId(applicationId > 0
            ? static_cast<uint64_t>(applicationId)
            : kApplicationId);
        gClient->SetStatusChangedCallback([](discordpp::Client::Status status,
                                             discordpp::Client::Error error,
                                             int32_t errorDetail) {
            gReady = status == discordpp::Client::Status::Ready;
            __android_log_print(ANDROID_LOG_INFO, kTag,
                "Discord SDK status=%d error=%d detail=%d",
                static_cast<int>(status), static_cast<int>(error), errorDetail);
        });
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gourmetal_retroalerts_MainActivity_nativeConnect(JNIEnv*, jobject) {
    if (gClient) gClient->Connect();
}

extern "C" JNIEXPORT void JNICALL
Java_com_gourmetal_retroalerts_MainActivity_nativeUpdatePresence(
    JNIEnv* env, jobject, jstring details, jstring state) {
    if (!gClient || !gReady) return;
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
        gReady = false;
    }
}
