#!/bin/bash
# Lance le serveur Tower Defense avec Java 21
cd "$(dirname "$0")"
JAVA21="$PWD/java21/Contents/Home/bin/java"
if [ ! -f "$JAVA21" ]; then
  echo "Java 21 non trouvée. Télécharge-la avec:"
  echo "curl -sL 'https://api.adoptium.net/v3/binary/latest/21/ga/mac/aarch64/jdk/hotspot/normal/eclipse?project=jdk' -o /tmp/jdk21.tar.gz"
  echo "mkdir -p java21 && tar -xzf /tmp/jdk21.tar.gz -C java21 --strip-components=1"
  exit 1
fi
exec "$JAVA21" -Xmx2G -jar fabric-server-launch.jar nogui
