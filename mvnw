#!/bin/sh
set -euf
BASEDIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$BASEDIR/.mvn/wrapper/maven-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
  echo "missing $WRAPPER_JAR" >&2
  exit 1
fi
if [ -z "${JAVA_HOME-}" ]; then
  JAVACMD="$(command -v java || true)"
else
  JAVACMD="$JAVA_HOME/bin/java"
fi
exec "$JAVACMD" ${MAVEN_OPTS:-} \
  -classpath "$WRAPPER_JAR" \
  "-Dmaven.multiModuleProjectDirectory=$BASEDIR" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
