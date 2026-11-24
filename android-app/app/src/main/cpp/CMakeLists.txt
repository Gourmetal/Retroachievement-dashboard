cmake_minimum_required(VERSION 3.22.1)
project(retroalerts_discord LANGUAGES CXX)

add_library(retroalerts_discord SHARED
    discord_bridge.cpp
)

set_target_properties(retroalerts_discord PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

target_include_directories(retroalerts_discord PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

add_library(discord_partner_sdk SHARED IMPORTED)
set_target_properties(discord_partner_sdk PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libdiscord_partner_sdk.so"
)

find_library(android-log log)

target_link_libraries(retroalerts_discord
    discord_partner_sdk
    ${android-log}
)
