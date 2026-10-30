# The client finds its platform with ServiceLoader, by the name in META-INF/services.
-keep class uz.dukeengine.client3d.android.AndroidPlatform { <init>(); }
-keep interface uz.dukeengine.client3d.platform.ClientPlatform
