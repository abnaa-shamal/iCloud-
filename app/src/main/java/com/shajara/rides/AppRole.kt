package com.shajara.rides

/** Which of the two apps this build is (set per flavor in build.gradle.kts). */
val IS_CAPTAIN_APP: Boolean = BuildConfig.CAPTAIN_APP

/** The role this app creates at registration and shows onboarding for. */
val APP_ROLE: String = if (IS_CAPTAIN_APP) ROLE_CAPTAIN else ROLE_PASSENGER
