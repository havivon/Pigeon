# Pigeon release rules. The libraries (Compose, Room, WorkManager, DataStore, Glance, Biometric)
# bring their own consumer rules; these cover what the app itself creates by name.

# Glance creates the widget buttons' callbacks (CopyCodeAction) from their class name
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# WorkManager creates the code cleanup worker from its class name
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Readable crash traces from the user's own phone (line numbers only, names stay obfuscated)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
