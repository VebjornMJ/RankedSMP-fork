#!/bin/sh
DEFAULT_JVM_OPTS=""
exec java -cp "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"

