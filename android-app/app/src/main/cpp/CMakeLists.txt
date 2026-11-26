cmake_minimum_required(VERSION 3.22.1)
project(retroalerts_discord LANGUAGES CXX)

find_package(discord_partner_sdk REQUIRED CONFIG)

add_library(retroalerts_discord SHARED
    discord_bridge.cpp
)

set_target_properties(retroalerts_discord PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

target_link_libraries(retroalerts_discord
    discord_partner_sdk::discord_partner_sdk
    log
)
