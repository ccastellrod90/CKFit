# Reglas de R8 para CKFit.

# Vosk usa JNA, que accede por reflexión a sus clases y a las que extienden de ellas
# (reglas de la demo oficial: github.com/alphacep/vosk-android-demo/blob/master/app/proguard-rules.pro).
-keep class com.sun.jna.* { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }
-dontwarn java.awt.**
