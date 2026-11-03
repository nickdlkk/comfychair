# Android/Compose/Lifecycle/WorkManager/Media3/OkHttp ship consumer rules.
# JSON is parsed explicitly with org.json: no reflective Gson/Moshi model keep.
# No application JNI, dynamic resource lookup, or reflection-based serialization.

# Default ViewModelProvider creates these through their public no-arg constructors.
-keepclassmembers,allowoptimization class sh.hnet.comfychair.viewmodel.** extends androidx.lifecycle.ViewModel {
    public <init>();
}

# WorkManager persists the worker class name and reflectively invokes this constructor.
-keep,allowoptimization class sh.hnet.comfychair.notification.GenerationCheckWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Preserve readable opt-in diagnostics; do not strip logging or realtime reporting.
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod
-keepnames class sh.hnet.comfychair.*Activity
-keepnames class sh.hnet.comfychair.connection.ConnectionState$*
-keepnames class sh.hnet.comfychair.connection.WebSocketState$*
# Keep this source-level diagnostics boundary auditable in release DEX/mapping.
-keep,allowoptimization class sh.hnet.comfychair.DiagnosticsApplication { *; }
-keep,allowoptimization class sh.hnet.comfychair.util.RealtimeDiagnostics { *; }
