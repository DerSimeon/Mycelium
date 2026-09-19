# Mycelium

[![Maven Central](https://img.shields.io/maven-central/v/lol.simeon/mycelium.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/lol.simeon/mycelium)
[![License](https://img.shields.io/badge/license-BSD--3--Clause-blue.svg)](LICENSE)

A small Kotlin library for reading and writing Minecraft protocol types over Netty buffers.

It wraps a Netty `ByteBuf` and gives you the primitives the Minecraft protocol actually uses (VarInts, length-prefixed strings, NBT, text components, and so on), handling the format changes between Minecraft versions for you. You give it a `Version`; it picks the right wire format.

## Why

The Minecraft protocol has a handful of encodings that shift between game versions. NBT went nameless on the network in 1.20.2. Text components switched from JSON strings to NBT in 1.20.3. If you write proxy or server code you end up reimplementing these every time. Mycelium is that code, in one place, tested.

## Install

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("lol.simeon:mycelium:1.0.0")
}
```

Gradle (Groovy):

```groovy
implementation 'lol.simeon:mycelium:1.0.0'
```

Maven:

```xml
<dependency>
    <groupId>lol.simeon</groupId>
    <artifactId>mycelium</artifactId>
    <version>1.0.0</version>
</dependency>
```

Netty and Adventure NBT come along as transitive `api` dependencies, since they show up in the public API.

## Usage

Wrap a `ByteBuf` in a `ByteMessage` and read or write:

```kotlin
val message = ByteMessage(buf)

// Basics
message.writeVarInt(773)
message.writeString("hello")
message.writeUUID(uuid)

val protocol = message.readVarInt()
val name = message.readString()
val id = message.readUUID()
```

Anything whose format depends on the protocol version takes a `Version`:

```kotlin
val version = Version.MINECRAFT_1_21

// NBT: named root before 1.20.2, nameless on the network after
message.writeCompoundTag(tag, version)
val tag = message.readCompoundTag(version)

// Text components: JSON string before 1.20.3, NBT after
message.writeComponent(Component.text("welcome"), version)
val component = message.readComponent(version)
```

Version comparison is by protocol number, so a point release between two known entries still resolves correctly:

```kotlin
if (version.isAtLeast(Version.MINECRAFT_1_20_2)) {
    // nameless NBT on the wire
}
```

## What's supported

- VarInts, strings (with optional max length), byte / int / long / string arrays
- UUIDs, BitSets, enum sets
- Namespaced keys (`minecraft:stone`), with defaulting to the `minecraft` namespace
- NBT compound tags and arrays, version-aware
- Text components, version-aware

Reads that hit malformed data throw `MyceliumReadException`; writes that get invalid input throw `MyceliumWriteException`.

## Requirements

- JDK 25
- Kotlin 2.4+

## Contributing

Issues and pull requests are welcome. If you're changing serialization behaviour, add a test in `src/test` alongside the existing `ByteMessageTest`. Run the build before opening a PR:

```bash
./gradlew build
```

`detekt` runs as part of the build, so keep the style clean.

## License

BSD 3-Clause. See [LICENSE](LICENSE).
