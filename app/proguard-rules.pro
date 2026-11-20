# Keep Signal protocol, JNI, Wire protos, and Jackson JSON models intact under R8
-keep class org.signal.** { *; }
-keep class org.whispersystems.** { *; }
-keep class com.squareup.wire.** { *; }
-keep class * extends com.squareup.wire.Message { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Keep our own Wire-generated protobuf classes
-keep class com.wearosignal.app.proto.** { *; }

# Keep Android / Wear services & receivers referenced from Manifest
-keep class com.wearosignal.app.tile.RecentTileService { *; }
-keep class com.wearosignal.app.complication.UnreadComplicationService { *; }
-keep class com.wearosignal.app.poll.** { *; }
-keep class com.wearosignal.app.notify.** { *; }

# Suppress harmless missing-class warnings from desktop JVM libraries pulled by libsignal-service
-dontwarn java.lang.instrument.**
-dontwarn javax.naming.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn kotlin.reflect.**
