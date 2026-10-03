# DevNotch – R8-Regeln für den Release-Build.
# Die meisten Bibliotheken (Room, Compose, Ktor, kotlinx-serialization ≥ 1.5, RevenueCat,
# Coil) bringen eigene Consumer-Regeln mit; hier steht nur, was darüber hinaus nötig ist.

# Lesbare Crash-Stacktraces (Zeilennummern bleiben, Dateinamen werden neutralisiert).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx-serialization -------------------------------------------------------------
# API-DTOs (GitHub, AI-Anbieter) und der AI-Usage-Cache werden über generierte Serializer
# gelesen. Die Bibliotheksregeln decken @Serializable-Klassen ab; zusätzlich die Companion-
# serializer()-Methoden unserer Pakete behalten, damit R8-Full-Mode sie nicht entfernt.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.frezzybuilds.devnotch.** {
    *** Companion;
    static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.frezzybuilds.devnotch.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Enums, die per Name gespeichert werden -----------------------------------------------
# SharedPreferences/Intents speichern Enum-Namen (Layout-Modus, Themes, Pro-Features,
# AI-Anbieter, Shortcut-Typ). Obfuskierte Namen würden gespeicherte Werte nach einem
# Update unlesbar machen.
-keepclassmembers enum com.frezzybuilds.devnotch.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Ktor ---------------------------------------------------------------------------------
# Optionale JVM-Abhängigkeiten, die es auf Android nicht gibt.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**
