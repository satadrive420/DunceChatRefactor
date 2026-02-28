# HikariCP Shading Configuration

## Problem
The plugin was failing to load with `NoClassDefFoundError: com/zaxxer/hikari/HikariConfig` because HikariCP was not included in the plugin JAR file.

## Solution
Configured the Shadow plugin to shade (bundle) HikariCP and its dependencies into the plugin JAR.

## Changes Made

### 1. Added Shadow Plugin
- Plugin: `io.github.goooler.shadow` version `8.1.8`
- This is a fork of the original Shadow plugin that supports Java 21 class files

### 2. Added Dependencies
```groovy
implementation 'com.zaxxer:HikariCP:5.1.0'
implementation 'org.slf4j:slf4j-api:2.0.9'
```

### 3. Configured Shading and Relocation
```groovy
shadowJar {
    archiveClassifier.set('')
    relocate 'com.zaxxer.hikari', 'gg.corn.DunceChat.libs.hikari'
    relocate 'org.slf4j', 'gg.corn.DunceChat.libs.slf4j'
    // Note: H2 is NOT relocated because JDBC DriverManager needs to find it via SPI
}
```

**What this does:**
- **Shading**: Bundles HikariCP, SLF4J, and H2 classes into your plugin JAR
- **Relocation**: Moves HikariCP and SLF4J to `gg.corn.DunceChat.libs` package to avoid conflicts
- **H2 Not Relocated**: H2 remains in its original `org.h2` package because JDBC `DriverManager` uses Service Provider Interface (SPI) to discover drivers, which requires the original package structure

### 4. Build Configuration
```groovy
build {
    dependsOn shadowJar
}

jar {
    enabled = false
}
```

This ensures the shaded JAR is always built and used as the primary artifact.

## How to Build

```bash
./gradlew clean build
```

Or specifically:
```bash
./gradlew shadowJar
```

## Verification

The final JAR (`build/libs/DunceChat-1.0-SNAPSHOT.jar`) includes:
- Your plugin classes
- HikariCP classes (relocated to `gg/corn/DunceChat/libs/hikari/`)
- SLF4J classes (relocated to `gg/corn/DunceChat/libs/slf4j/`)
- H2 Database classes (in original `org/h2/` package - NOT relocated)
- Size: ~2.8 MB

## Why Relocation?

Relocation prevents conflicts when:
1. Multiple plugins use different versions of HikariCP
2. The server itself uses HikariCP
3. Other plugins load HikariCP in a different classloader

By relocating, your plugin gets its own isolated copy of HikariCP that won't interfere with anything else.

## No Source Code Changes Required

The Shadow plugin automatically rewrites bytecode references, so you can still use:
```java
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
```

These imports work fine in source code and are automatically rewritten in the compiled classes.

