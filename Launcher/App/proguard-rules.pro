# DataStore
-keep class fi.iki.elonen.** { *; }
-keep class androidx.datastore.** { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
# RxJava
-keepclassmembers class rx.** { *; }
-keep class io.reactivex.** { *; }
-dontwarn io.reactivex.**
-keep class androidx.datastore.rxjava3.RxDataStore { *; }
