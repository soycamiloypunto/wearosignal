# Keep Signal protocol, JNI, Wire protos, and Jackson JSON models intact under R8
-keep class org.signal.** { *; }
-keep class org.whispersystems.** { *; }
-keep class com.squareup.wire.** { *; }
-keep class * extends com.squareup.wire.Message { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Keep our own Wire-generated protobuf classes
-keep class com.wearosignal.app.protos.** { *; }

# Keep Room, WorkManager, Startup, and ViewModel reflection constructors under R8 fullMode
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class * extends androidx.startup.Initializer { <init>(); }
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# Keep Android / Wear services & receivers referenced from Manifest
-keep class com.wearosignal.app.tile.RecentTileService { *; }
-keep class com.wearosignal.app.tile.TilePollWorker { *; }
-keep class com.wearosignal.app.complication.UnreadComplicationService { *; }
-keep class com.wearosignal.app.poll.** { *; }
-keep class com.wearosignal.app.notify.** { *; }

# Suppress harmless missing-class warnings from desktop JVM libraries pulled by libsignal-service
-dontwarn java.lang.instrument.**
-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.**
-dontwarn javax.naming.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn kotlin.reflect.**
