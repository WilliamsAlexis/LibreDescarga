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
