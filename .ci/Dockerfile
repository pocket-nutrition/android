# SPDX-License-Identifier: MPL-2.0
#
# Build environment for pocket-nutrition-android.
#
# WHY A DEDICATED IMAGE
# Installing the Android SDK on every CI run costs several minutes of download before a single line
# compiles, and makes every build depend on dl.google.com staying up. Baking it in makes the pipeline
# fast and hermetic.
#
# WHY IT LIVES HERE rather than in pocket-nutrition-ci-tools: that repo builds one image (Alpine +
# kubectl) from a Dockerfile at its root, and its published path is already consumed by the api and
# sandbox deploy jobs. Adding a second image there would mean restructuring it and risking those.
# The Android build environment is also a detail of this repo, so it belongs to this repo.
#
# No CI pipeline is included in this fork — if you add one, build and push this image only when
# this file changes, not on every commit.

FROM eclipse-temurin:21-jdk-noble

# Pinned deliberately. An unpinned SDK is a build that changes under you: a new build-tools release
# can alter lint output with no commit in this repository to explain it.
#
# CMDLINE_TOOLS must be recent enough to understand the repository metadata, not merely present. An
# older build (13114758) warns "This version only understands SDK XML versions up to 3 but an SDK XML
# file of version 4 was encountered" and then reports `Failed to find package 'platforms;android-37'`
# for a platform that does exist — a missing-package error that is really a stale-parser error.
# Latest build numbers come from https://dl.google.com/android/repository/repository2-4.xml
ARG CMDLINE_TOOLS=15859902
#
# PLATFORM is the full package name, and SDK 37 platforms are versioned: the package is
# `platforms;android-37.0`, not `platforms;android-37`. `compileSdk = 37` in app/build.gradle.kts
# resolves to exactly this one — verified by letting AGP download it and reading back the directory
# name. Getting this wrong is silent: AGP auto-downloads the platform it needs at build time, so the
# image just quietly stops saving any work.
ARG PLATFORM=android-37.0
ARG BUILD_TOOLS=37.0.0

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/opt/gradle-home \
    DEBIAN_FRONTEND=noninteractive

# git is needed by actions/checkout, unzip by the SDK installer. No recommends, and the apt lists are
# dropped in the same layer so they never reach the published image.
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl unzip git ca-certificates xz-utils \
    && rm -rf /var/lib/apt/lists/*

# NODE IS NOT OPTIONAL, even though nothing in this project is JavaScript.
#
# Most Actions-compatible CI runners (including GitHub Actions) run JavaScript actions —
# actions/checkout@v4, actions/upload-artifact — *inside* the job container, using a `node` binary
# they expect to find there. Without it every container job dies at checkout with:
#
#     exec: "node": executable file not found in $PATH   (exit 127)
#
# which reads like a broken workflow rather than a missing runtime. Installed from the official
# tarball with a pinned version rather than by piping a remote setup script into a shell.
ARG NODE_VERSION=24.18.0
RUN ARCH="$(dpkg --print-architecture)" \
    && case "${ARCH}" in \
         amd64) NODE_ARCH=x64 ;; \
         arm64) NODE_ARCH=arm64 ;; \
         *) echo "unsupported architecture ${ARCH}" >&2; exit 1 ;; \
       esac \
    && curl -fsSL -o /tmp/node.tar.xz \
        "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${NODE_ARCH}.tar.xz" \
    && tar -xJf /tmp/node.tar.xz -C /usr/local --strip-components=1 \
        --exclude=CHANGELOG.md --exclude=LICENSE --exclude=README.md \
    && rm /tmp/node.tar.xz \
    && node --version

RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS}_latest.zip" \
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
    && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip

ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools"

# `yes |` accepts the SDK licences non-interactively. That is the Android SDK licence, which every
# Android build must accept; it has no bearing on this project's AGPL licensing.
RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install \
        "platforms;${PLATFORM}" \
        "build-tools;${BUILD_TOOLS}" \
        "platform-tools" \
    && rm -rf "${ANDROID_HOME}/.temp" /root/.android

# A writable Gradle home so a non-root CI user can still populate the dependency cache. Warming it
# further would need the project's dependency graph, which belongs to the build job, not the image.
RUN mkdir -p "${GRADLE_USER_HOME}"

# THE GRADLE DISTRIBUTION IS BAKED IN, for the same reason as the SDK. Every job used to download it
# from services.gradle.org through the wrapper, and on 2026-11-03 that download timed out on three runs
# in a row (at 10 s, then at 60 s) while the build job of the same run fetched it fine: a flaky network
# failing jobs before a line compiled. Running the project's own wrapper here fills
# ${GRADLE_USER_HOME}/wrapper/dists with exactly the distribution and checksum it pins, under the
# directory name the wrapper derives from the URL, which a hand-placed zip would have to reproduce.
# ci-image.yml rebuilds the image when gradle-wrapper.properties changes, so a Gradle bump is picked up.
COPY gradlew /tmp/gradle-prefetch/gradlew
COPY gradle/wrapper /tmp/gradle-prefetch/gradle/wrapper
RUN cd /tmp/gradle-prefetch \
    && ./gradlew --version --no-daemon \
    && rm -rf /tmp/gradle-prefetch "${GRADLE_USER_HOME}/daemon" "${GRADLE_USER_HOME}/native"

RUN chmod -R 0777 "${GRADLE_USER_HOME}"

# Fail the image build rather than the first pipeline run if a component did not land.
RUN test -d "${ANDROID_HOME}/platforms/${PLATFORM}" \
    && test -x "${ANDROID_HOME}/build-tools/${BUILD_TOOLS}/aapt2" \
    && ls -d "${GRADLE_USER_HOME}"/wrapper/dists/gradle-*-bin/*/gradle-* \
    && java -version \
    && node --version \
    && echo "ci-android ready: JDK 21, Node ${NODE_VERSION}, platform ${PLATFORM}, build-tools ${BUILD_TOOLS}, Gradle prefetched"
