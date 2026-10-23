# ===========================================================================
# Reglas de R8/ProGuard de LibreDescarga
# La ofuscación está activada; aquí solo se conserva lo que se usa por reflexión.
# ===========================================================================

# Trazas de error legibles en los reportes que el usuario envíe manualmente.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Rutas de Navigation Compose (kotlinx.serialization): se resuelven por su serializador.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.libredescarga.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.libredescarga.**$$serializer { *; }
-keepclassmembers class com.libredescarga.** {
    *** Companion;
}

# Motor de extracción (youtubedl-android): usa reflexión (Jackson) y carga nativa.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.databind.** { *; }
-keepclassmembers class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry

# Enumeraciones guardadas por nombre en DataStore y Room.
-keepclassmembers enum com.libredescarga.core.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Cifrado de la carpeta privada (Tink): usa clases generadas y registro por reflexión.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.joda.time.**

# Descompresión de Python/yt-dlp/FFmpeg al iniciar el motor: commons-compress registra sus clases por reflexión
# (ExtraFieldUtils) y R8 la rompía en la versión release ("Ocurrió un error: ih1").
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.io.**

# NewPipe Extractor (vía rápida de YouTube): Rhino ejecuta el JavaScript del reproductor y se carga por reflexión.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn org.mozilla.javascript.engine.**
-dontwarn javax.script.**
# Mensajes protobuf "lite" que NewPipe usa con YouTube (se leen por reflexión).
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
# Rhino trae soporte para "invokedynamic" de Java de escritorio (jdk.dynalink); en Android usa el modo interpretado.
-dontwarn jdk.dynalink.**
