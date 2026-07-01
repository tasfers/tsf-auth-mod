-ignorewarnings
-dontwarn **
-dontoptimize

-allowaccessmodification
-repackageclasses 'a'
-flattenpackagehierarchy 'a'
-overloadaggressively

-keepattributes Signature,Exceptions,*Annotation*

# Fabric entry points
-keep class com.tasfers.tsfauth.TsfAuthClient {
    public void onInitializeClient();
}
-keep class com.tasfers.tsfauth.TsfAuthPreLaunch {
    public void onPreLaunch();
}

-keep class * implements net.fabricmc.api.ModInitializer
-keep class * implements net.fabricmc.api.ClientModInitializer
-keep class * implements net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint

-keep interface net.fabricmc.api.ModInitializer { *; }
-keep interface net.fabricmc.api.ClientModInitializer { *; }
-keep interface net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint { *; }
-keep class net.fabricmc.api.** { *; }
-keep class net.fabricmc.loader.api.** { *; }

# Keep GUI Screens to prevent rendering breakage
-keep class com.tasfers.tsfauth.AuthScreen { *; }
-keep class com.tasfers.tsfauth.AuthScreen$* { *; }
-keep class com.tasfers.tsfauth.AccountListScreen { *; }
-keep class com.tasfers.tsfauth.AccountListScreen$* { *; }

# Keep SkinFetcher and its anonymous classes since they implement Vanilla interfaces
-keep class com.tasfers.tsfauth.SkinFetcher { *; }
-keep class com.tasfers.tsfauth.SkinFetcher$* { *; }

# Must keep Mixins to prevent Mixin engine crash
-keep class com.tasfers.tsfauth.mixin.** { *; }

# Keep data classes used in JSON serialization
-keep class com.tasfers.tsfauth.AccountManager$Account { *; }
-keep class com.tasfers.tsfauth.config.ModConfig$ConfigData { *; }

# ModMenu integration
-keep class com.tasfers.tsfauth.integration.ModMenuIntegration { *; }
