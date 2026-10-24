# --- remotePool: Gson/Retrofit работают через рефлексию, R8 не должен трогать модели ---
# Раньше было только "-keepclassmembers ... { <fields>; }": поля сохранялись, но сами
# классы-типы (например VersionSecret) R8 мог склеить с другими классами (class merging),
# после чего Gson видел в поле чужой/абстрактный класс -> "Abstract classes can't be instantiated".

# Generic-сигнатуры (List<String>, Response<T>, suspend-функции Retrofit) и аннотации
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Модели, которые (де)сериализуются Gson: хранилище и ответы API
-keep class com.remotepool.models.** { *; }
-keep class com.remotepool.VersionSecret { *; }
-keep class com.remotepool.ResponseSecret { *; }
# Запросы авторизации Keystone (private data class'ы в RemotePoolRepository.kt)
-keep class com.remotepool.Ks* { *; }

# Gson: TypeAdapter'ы и @SerializedName
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
