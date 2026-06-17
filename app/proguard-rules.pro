# XiangQin - ProGuard / R8 规则

# ======== Room ========
-keep class com.xiangqin.app.data.db.** { *; }

# ======== SQLCipher ========
-keep class net.sqlcipher.** { *; }
-keep class net.zetetic.** { *; }

# ======== Kotlin Serialization ========
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.xiangqin.app.data.db.**$$serializer { *; }
-keepclassmembers class com.xiangqin.app.data.db.** {
    *** Companion;
}
-keepclasseswithmembers class com.xiangqin.app.data.db.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ======== Ktor ========
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ======== Netty (ktor-server-netty) ========
-dontwarn io.netty.**
-keep class io.netty.** { *; }
-dontwarn reactor.blockhound.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

# ======== Kotlinx Coroutines ========
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ======== WorkManager ========
-keep class * extends androidx.work.Worker { *; }

# ======== 我们自己定义的 data class (序列化) ========
-keep class com.xiangqin.app.server.** { *; }
-keep class com.xiangqin.app.data.datastore.** { *; }
-keep class com.xiangqin.app.monitor.** { *; }
