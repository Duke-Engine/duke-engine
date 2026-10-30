# The client finds its platform with ServiceLoader, by the name in META-INF/services.
-keep class uz.dukeengine.client3d.android.AndroidPlatform { <init>(); }
-keep interface uz.dukeengine.client3d.platform.ClientPlatform
# jME makes its buffer allocator by name (DukeActivity.nativeBuffers).
-keep class com.jme3.util.AndroidNativeBufferAllocator { <init>(); }
