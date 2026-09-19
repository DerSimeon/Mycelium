package lol.simeon.mycelium

import io.netty.buffer.Unpooled
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ByteMessageTest {

    private fun msg() = ByteMessage(Unpooled.buffer())

    // --- varInt ---

    @Test
    fun `varInt roundtrips across byte-count boundaries`() {
        // Boundaries where the encoded length flips to the next byte.
        val values = listOf(
            0, 1, 127, 128, 255,
            16_383, 16_384,
            2_097_151, 2_097_152,
            268_435_455, 268_435_456,
            Int.MAX_VALUE,
        )
        for (value in values) {
            val m = msg()
            m.writeVarInt(value)
            assertEquals(value, m.readVarInt(), "roundtrip failed for $value")
        }
    }

    @Test
    fun `varInt uses the minimum number of bytes`() {
        val m = msg()
        m.writeVarInt(300) // fits in 2 bytes
        assertEquals(2, m.toByteArray().size)
    }

    @Test
    fun `varInt roundtrips negatives as the 5-byte form`() {
        // VarInts are signed; negatives are spec-legal and encode to 5 bytes.
        for (value in listOf(-1, -128, Int.MIN_VALUE)) {
            val m = msg()
            m.writeVarInt(value)
            assertEquals(value, m.readVarInt(), "roundtrip failed for $value")
        }
    }

    @Test
    fun `readVarInt rejects oversized`() {
        val m = msg()
        repeat(6) { m.buf.writeByte(0x80) } // 6 continuation bytes > maxReads
        assertFailsWith<MyceliumReadException> { m.readVarInt() }
    }

    // --- varLong ---

    @Test
    fun `varLong roundtrips across byte-count boundaries`() {
        // Boundaries where the encoded length flips to the next byte, plus the extremes.
        val values = listOf(
            0L, 1L, 127L, 128L, 255L,
            16_383L, 16_384L,
            2_097_151L, 2_097_152L,
            268_435_455L, 268_435_456L,
            Int.MAX_VALUE.toLong(),
            34_359_738_367L, 34_359_738_368L,
            Long.MAX_VALUE,
        )
        for (value in values) {
            val m = msg()
            m.writeVarLong(value)
            assertEquals(value, m.readVarLong(), "roundtrip failed for $value")
        }
    }

    @Test
    fun `varLong roundtrips negatives as the 10-byte form`() {
        // VarLongs are signed; negatives are spec-legal and encode to 10 bytes.
        for (value in listOf(-1L, -128L, Long.MIN_VALUE)) {
            val m = msg()
            m.writeVarLong(value)
            assertEquals(value, m.readVarLong(), "roundtrip failed for $value")
        }
    }

    @Test
    fun `readVarLong rejects oversized`() {
        val m = msg()
        repeat(11) { m.buf.writeByte(0x80) } // more continuation bytes than a valid varLong
        assertFailsWith<MyceliumReadException> { m.readVarLong() }
    }

    // --- boolean ---

    @Test
    fun `boolean roundtrips`() {
        for (value in listOf(true, false)) {
            val m = msg()
            m.writeBoolean(value)
            assertEquals(value, m.readBoolean(), "roundtrip failed for $value")
        }
    }

    // --- angle ---

    @Test
    fun `angle roundtrips at byte-representable degrees`() {
        // Multiples of 45 within the signed-byte range land exactly on the 256-step grid.
        // The byte is signed, so angles read back in [-180, 180): 270 would return -90.
        for (deg in listOf(0f, 45f, 90f, -45f, -90f)) {
            val m = msg()
            m.writeAngle(deg)
            assertEquals(deg, m.readAngle(), "roundtrip failed for $deg")
        }
    }

    @Test
    fun `angle encodes to a single byte`() {
        val m = msg()
        m.writeAngle(90f)
        assertEquals(1, m.toByteArray().size)
    }

    @Test
    fun `angle quantises off-grid degrees to the nearest step`() {
        // 10 deg -> 10 / (360/256) = 7.11 -> byte 7 -> 7 * (360/256) = 9.84375
        val m = msg()
        m.writeAngle(10f)
        assertEquals(7.toByte(), m.toByteArray()[0])
        assertEquals(9.84375f, msg().apply { writeAngle(10f) }.readAngle())
    }

    // --- fixed-point ---

    @Test
    fun `fixedPointInt roundtrips at 1_32 increments`() {
        for (value in listOf(0.0, 1.0, -1.0, 12.5, -0.03125, 100.09375)) {
            val m = msg()
            m.writeFixedPointInt(value)
            assertEquals(value, m.readFixedPointInt(), "roundtrip failed for $value")
        }
    }

    @Test
    fun `fixedPointInt floors off-grid values toward negative infinity`() {
        // 1.6 * 32 = 51.2 -> floor 51 -> 51/32 = 1.59375
        assertEquals(1.59375, msg().apply { writeFixedPointInt(1.6) }.readFixedPointInt())
        // -1.6 * 32 = -51.2 -> floor -52 -> -52/32 = -1.625
        assertEquals(-1.625, msg().apply { writeFixedPointInt(-1.6) }.readFixedPointInt())
    }

    @Test
    fun `fixedPointInt encodes to four bytes`() {
        assertEquals(4, msg().apply { writeFixedPointInt(5.0) }.toByteArray().size)
    }

    // --- position ---

    @Test
    fun `position roundtrips on 1_14+ including signed extremes`() {
        val values = listOf(
            Position(0, 0, 0),
            Position(1, 2, 3),
            Position(-1, -1, -1),
            Position(33554431, 2047, 33554431),   // max 26/12/26 bit signed
            Position(-33554432, -2048, -33554432), // min
        )
        for (value in values) {
            val m = msg()
            m.writePosition(value, Version.MINECRAFT_1_14)
            assertEquals(value, m.readPosition(Version.MINECRAFT_1_14), "roundtrip failed for $value")
        }
    }

    @Test
    fun `position roundtrips on pre-1_14 including signed extremes`() {
        val values = listOf(
            Position(0, 0, 0),
            Position(1, 2, 3),
            Position(-1, -1, -1),
            Position(33554431, 2047, 33554431),
            Position(-33554432, -2048, -33554432),
        )
        for (value in values) {
            val m = msg()
            m.writePosition(value, Version.MINECRAFT_1_8)
            assertEquals(value, m.readPosition(Version.MINECRAFT_1_8), "roundtrip failed for $value")
        }
    }

    @Test
    fun `position field order differs across the 1_14 boundary`() {
        val value = Position(1, 2, 3)
        val old = msg().apply { writePosition(value, Version.MINECRAFT_1_13) }.toByteArray()
        val new = msg().apply { writePosition(value, Version.MINECRAFT_1_14) }.toByteArray()
        assertTrue(!old.contentEquals(new), "pre/post 1.14 packings should differ")
    }

    // --- optional ---

    @Test
    fun `optional roundtrips present and absent`() {
        msg().let { m ->
            m.writeOptional("hi") { m.writeString(it) }
            assertEquals("hi", m.readOptional { m.readString() })
        }
        msg().let { m ->
            m.writeOptional<String>(null) { m.writeString(it) }
            assertEquals(null, m.readOptional { m.readString() })
        }
    }

    // --- list ---

    @Test
    fun `list roundtrips`() {
        val value = listOf(1, 2, 3, 42)
        val m = msg()
        m.writeList(value) { m.writeVarInt(it) }
        assertEquals(value, m.readList { m.readVarInt() })
    }

    // --- primitives / arrays ---

    @Test
    fun `string roundtrips including multibyte utf8`() {
        for (value in listOf("", "hello", "café", "emoji 🎈 mix", "a".repeat(1000))) {
            val m = msg()
            m.writeString(value)
            assertEquals(value, m.readString(), "roundtrip failed for '$value'")
        }
    }

    @Test
    fun `readString rejects a string longer than maxLength`() {
        val m = msg()
        m.writeString("x".repeat(100))
        assertFailsWith<MyceliumReadException> { m.readString(10) }
    }

    @Test
    fun `byteArray roundtrips`() {
        val value = byteArrayOf(-128, -1, 0, 1, 42, 127)
        val m = msg()
        m.writeByteArray(value)
        assertContentEquals(value, m.readByteArray())
    }

    @Test
    fun `intArray roundtrips`() {
        val value = intArrayOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)
        val m = msg()
        m.writeIntArray(value)
        assertContentEquals(value, m.readIntArray())
    }

    @Test
    fun `longArray roundtrips`() {
        val value = longArrayOf(Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE)
        val m = msg()
        m.writeLongArray(value)
        assertContentEquals(value, m.readLongArray())
    }

    @Test
    fun `bitSet roundtrips`() {
        val value = longArrayOf(0b1011, 0, Long.MAX_VALUE)
        val m = msg()
        m.writeBitSet(value)
        assertContentEquals(value, m.readBitSet())
    }

    @Test
    fun `stringArray roundtrips`() {
        val value = arrayOf("a", "", "unicode ✓", "b")
        val m = msg()
        m.writeStringArray(value)
        assertContentEquals(value, m.readStringArray())
    }

    @Test
    fun `uuid roundtrips`() {
        val value = UUID.randomUUID()
        val m = msg()
        m.writeUUID(value)
        assertEquals(value, m.readUUID())
    }

    @Test
    fun `readArray roundtrips against writeByteArray and rejects oversize`() {
        val value = byteArrayOf(1, 2, 3, 4)
        msg().let { m ->
            m.writeByteArray(value)
            assertContentEquals(value, m.readArray())
        }
        msg().let { m ->
            m.writeByteArray(value)
            assertFailsWith<MyceliumReadException> { m.readArray(2) }
        }
    }

    // --- namespaced keys ---

    @Test
    fun `namespacedKey roundtrips for default and custom namespaces`() {
        for (key in listOf(NamespacedKey.minecraft("stone"), NamespacedKey("mymod", "widget"))) {
            val m = msg()
            m.writeNamespacedKey(key)
            assertEquals(key, m.readNamespacedKey())
        }
    }

    @Test
    fun `namespacedKey array roundtrips`() {
        val value = arrayOf(NamespacedKey.minecraft("dirt"), NamespacedKey("mymod", "gear"))
        val m = msg()
        m.writeNamespacedKeyArray(value)
        assertContentEquals(value, m.readNamespacedKeyArray())
    }

    // --- enum sets ---

    @Test
    fun `enumSet roundtrips`() {
        val value = setOf(Version.MINECRAFT_1_8, Version.MINECRAFT_1_20_3, Version.MINECRAFT_26_1)
        val m = msg()
        m.writeEnumSet(value)
        assertEquals(value, m.readEnumSet(Version::class.java))
    }

    // --- compound tags ---

    private val sampleTag = CompoundBinaryTag.builder()
        .putString("name", "notch")
        .putInt("count", 5)
        .build()

    @Test
    fun `named compoundTag roundtrips on pre-1_20_2`() {
        val m = msg()
        m.writeCompoundTag(sampleTag, Version.MINECRAFT_1_20)
        assertEquals(sampleTag, m.readCompoundTag(Version.MINECRAFT_1_20))
    }

    @Test
    fun `nameless compoundTag roundtrips on 1_20_2+`() {
        val m = msg()
        m.writeCompoundTag(sampleTag, Version.MINECRAFT_1_20_2)
        assertEquals(sampleTag, m.readCompoundTag(Version.MINECRAFT_1_20_2))
    }

    @Test
    fun `nameless compoundTag drops the root name on 1_20_2+`() {
        val m = msg()
        m.writeCompoundTag(sampleTag, Version.MINECRAFT_1_20_2)
        val nameless = m.toByteArray()

        val baos = ByteArrayOutputStream()
        BinaryTagIO.writer().write(sampleTag, baos)
        val named = baos.toByteArray()

        // nameless = named with the 2-byte empty-name length (bytes 1..2) removed
        val expected = byteArrayOf(named[0]) + named.copyOfRange(3, named.size)
        assertContentEquals(expected, nameless)
    }

    @Test
    fun `compoundTag array roundtrips`() {
        val value = arrayOf(sampleTag, CompoundBinaryTag.builder().putByte("flag", 1).build())
        val m = msg()
        m.writeCompoundTagArray(value, Version.MINECRAFT_1_20_2)
        assertContentEquals(value, m.readCompoundTagArray(Version.MINECRAFT_1_20_2))
    }

    // --- components ---

    @Test
    fun `component roundtrips as JSON pre-1_20_3`() {
        val component = Component.text("hello").color(NamedTextColor.RED)
        val m = msg()
        m.writeComponent(component, Version.MINECRAFT_1_20)
        assertEquals(component, m.readComponent(Version.MINECRAFT_1_20))
    }

    @Test
    fun `component roundtrips as NBT on 1_20_3+ including a boolean field`() {
        // bold exercises the byte<->boolean bridge; children exercise list handling.
        val component = Component.text("hello")
            .color(NamedTextColor.RED)
            .decoration(TextDecoration.BOLD, true)
            .append(Component.text(" world").color(NamedTextColor.BLUE))
        val m = msg()
        m.writeComponent(component, Version.MINECRAFT_1_20_3)
        assertEquals(component, m.readComponent(Version.MINECRAFT_1_20_3))
    }

    @Test
    fun `component NBT and JSON encodings differ across the 1_20_3 boundary`() {
        val component = Component.text("hi").color(NamedTextColor.GREEN)
        val json = msg().apply { writeComponent(component, Version.MINECRAFT_1_20_2) }.toByteArray()
        val nbt = msg().apply { writeComponent(component, Version.MINECRAFT_1_20_3) }.toByteArray()
        assertTrue(!json.contentEquals(nbt), "JSON and NBT encodings should differ")
    }
}
