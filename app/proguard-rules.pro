# kotlinx-serialization（反射式 JSON 解析用）
-keepclassmembers class dev.minis.tokendock.** {
    *** Companion;
}
-keepclasseswithmembers class dev.minis.tokendock.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.minis.tokendock.**$$serializer { *; }
