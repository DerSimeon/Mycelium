package lol.simeon.mycelium

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import net.kyori.adventure.nbt.BinaryTag
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.ByteBinaryTag
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.DoubleBinaryTag
import net.kyori.adventure.nbt.FloatBinaryTag
import net.kyori.adventure.nbt.IntBinaryTag
import net.kyori.adventure.nbt.ListBinaryTag
import net.kyori.adventure.nbt.LongBinaryTag
import net.kyori.adventure.nbt.ShortBinaryTag
import net.kyori.adventure.nbt.StringBinaryTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.SequenceInputStream
import java.lang.Enum.valueOf
import java.util.*
import kotlin.math.floor

class ByteMessage(val buf: ByteBuf) {

    val maxReads: Int = MAX_VARINT_BYTES

    fun toByteArray(): ByteArray {
        val byteArray = ByteArray(buf.readableBytes())
        buf.readBytes(byteArray)
        return byteArray
    }

    /**
     * Reads a minecraft varInt from the buffer.
     * @return the varInt read from the buffer
     */
    fun readVarInt(): Int {
        var numRead = 0
        var result = 0
        var read: Byte
        do {
            read = buf.readByte()
            val value = read.toInt() and SEGMENT_BITS
            result = result or (value shl (SEGMENT_SHIFT * numRead))

            numRead++
            if (numRead > maxReads) {
                throw MyceliumReadException("VarInt is too big")
            }
        } while ((read.toInt() and CONTINUE_BIT) != 0)

        return result
    }

    /**
     * Writes a minecraft varInt to the buffer.
     * @param value the varInt to write to the buffer
     */
    fun writeVarInt(value: Int) {
        if (value < 0) {
            throw MyceliumWriteException("VarInt cannot be negative")
        }
        var remaining = value
        while ((remaining and SEGMENT_BITS.inv()) != 0) {
            buf.writeByte((remaining and SEGMENT_BITS) or CONTINUE_BIT)
            remaining = remaining ushr SEGMENT_SHIFT
        }
        buf.writeByte(remaining)
    }

    /**
     * Reads a minecraft string from the buffer.
     * @return the string read from the buffer
     */
    fun readString(): String {
        return readString(Short.MAX_VALUE.toInt())
    }

    /**
     * Reads a minecraft string from the buffer with a maximum length.
     * @param maxLength the maximum length of the string to read
     * @return the string read from the buffer
     */
    fun readString(maxLength: Int): String {
        val length = readVarInt()
        val maxBytes = maxLength * MAX_BYTES_PER_CHAR
        if (length > maxBytes) {
            throw MyceliumReadException("String byte length too long: $length > $maxBytes")
        }

        val string = buf.readString(length, Charsets.UTF_8)
        if (string.length > maxLength) {
            throw MyceliumReadException("String too long: ${string.length} > $maxLength")
        }

        return string
    }

    /**
     * Writes a minecraft string to the buffer.
     * @param value the string to write to the buffer
     */
    fun writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarInt(bytes.size)
        buf.writeBytes(bytes)
    }

    /**
     * Reads a minecraft byte array from the buffer.
     * @return the byte array read from the buffer
     */
    fun readByteArray(): ByteArray {
        val length = readVarInt()
        val byteArray = ByteArray(length)
        buf.readBytes(byteArray)
        return byteArray
    }

    /**
     * Writes a minecraft byte array to the buffer.
     * @param value the byte array to write to the buffer
     */
    fun writeByteArray(value: ByteArray) {
        writeVarInt(value.size)
        buf.writeBytes(value)
    }

    /**
     * Reads a minecraft int array from the buffer.
     * @return the int array read from the buffer
     */
    fun readIntArray(): IntArray {
        val length = readVarInt()
        val intArray = IntArray(length)
        for (i in 0 until length) {
            intArray[i] = buf.readInt()
        }
        return intArray
    }

    /**
     * Writes a minecraft int array to the buffer.
     * @param value the int array to write to the buffer
     */
    fun writeIntArray(value: IntArray) {
        writeVarInt(value.size)
        for (i in value) {
            buf.writeInt(i)
        }
    }

    /**
     * Reads a minecraft uuid from the buffer.
     * @return the uuid from the buffer
     */
    fun readUUID(): UUID {
        val mostSigBits = buf.readLong()
        val leastSigBits = buf.readLong()
        return UUID(mostSigBits, leastSigBits)
    }

    /**
     * Writes a minecraft uuid to the buffer.
     * @param value the uuid to write to the buffer
     */
    fun writeUUID(value: UUID) {
        buf.writeLong(value.mostSignificantBits)
        buf.writeLong(value.leastSignificantBits)
    }

    /**
     * reads a minecraft string array from the buffer.
     * @return the string array read from the buffer
     */
    fun readStringArray(): Array<String> {
        val length = readVarInt()
        val stringArray = Array(length) { "" }
        for (i in 0 until length) {
            stringArray[i] = readString()
        }
        return stringArray
    }

    /**
     * writes a minecraft string array to the buffer.
     * @param value the string array to write to the buffer
     */
    fun writeStringArray(value: Array<String>) {
        writeVarInt(value.size)
        for (i in value) {
            writeString(i)
        }
    }

    /**
     * reads a minecraft long array from the buffer.
     * @return the long array read from the buffer
     */
    fun readLongArray(): LongArray {
        val length = readVarInt()
        val longArray = LongArray(length)
        for (i in 0 until length) {
            longArray[i] = buf.readLong()
        }
        return longArray
    }

    /**
     * writes a minecraft long array to the buffer.
     * @param value the long array to write to the buffer
     */
    fun writeLongArray(value: LongArray) {
        writeVarInt(value.size)
        for (i in value) {
            buf.writeLong(i)
        }
    }

    /**
     * reads a minecraft bitset from the buffer.
     * @return the bitset read from the buffer
     */
    fun readBitSet(): LongArray {
        val length = readVarInt()
        val bitSet = LongArray(length)
        for (i in 0 until length) {
            bitSet[i] = buf.readLong()
        }
        return bitSet
    }

    /**
     * writes a minecraft bitset to the buffer.
     * @param value the bitset to write to the buffer
     */
    fun writeBitSet(value: LongArray) {
        writeLongArray(value)
    }

    /**
     * reads a minecraft compoundTag from the buffer.
     *
     * Mirrors [writeCompoundTag]: nameless network form since [Version.MINECRAFT_1_20_2],
     * named root form before that.
     *
     * @param version the protocol version being read for
     * @return the compoundTag read from the buffer
     */
    fun readCompoundTag(version: Version): CompoundBinaryTag {
        if (version.isAtLeast(Version.MINECRAFT_1_20_2)) {
            return readNamelessCompound()
        }
        // read via DataInput: consumes exactly the tag, unlike the buffering InputStream
        // overload which would drain the shared buffer past this tag.
        return BinaryTagIO.reader().read(ByteBufInputStream(buf) as DataInput)
    }

    /**
     * writes a minecraft compoundTag to the buffer.
     *
     * Since [Version.MINECRAFT_1_20_2] network NBT is nameless
     *
     * @param value the compoundTag to write to the buffer
     * @param version the protocol version being written for
     */
    fun writeCompoundTag(value: CompoundBinaryTag, version: Version) {
        if (version.isAtLeast(Version.MINECRAFT_1_20_2)) {
            writeNamelessCompound(value)
        } else {
            BinaryTagIO.writer().write(value, ByteBufOutputStream(buf) as OutputStream)
        }
    }

    /**
     * Writes a compound as the nameless network form: type id followed by payload,
     * with no root name (as used since [Version.MINECRAFT_1_20_2]).
     *
     * adventure only emits the named root form, so write that and drop the 2-byte
     * empty-name length that follows the type id.
     * ponytail: buffers the whole tag to strip 2 bytes; fine unless NBT encode is hot.
     */
    private fun writeNamelessCompound(value: CompoundBinaryTag) {
        val named = ByteArrayOutputStream()
        BinaryTagIO.writer().write(value, named)
        val bytes = named.toByteArray()
        buf.writeByte(bytes[0].toInt()) // TAG_Compound id (0x0A)
        // skip the type id + 2-byte empty name, keeping only the payload
        buf.writeBytes(bytes, NAMED_ROOT_HEADER_LENGTH, bytes.size - NAMED_ROOT_HEADER_LENGTH)
    }

    /**
     * reads a minecraft comboundTagArray from the buffer.
     * @return the compoundTagArray read from the buffer
     */
    fun readCompoundTagArray(version: Version): Array<CompoundBinaryTag> {
        val length = readVarInt()
        val compoundTagArray = Array(length) { CompoundBinaryTag.empty() }
        for (i in 0 until length) {
            compoundTagArray[i] = readCompoundTag(version)
        }
        return compoundTagArray
    }

    /**
     * writes a minecraft comboundTagArray to the buffer.
     * @param value the compoundTagArray to write to the buffer
     * @param version the protocol version being written for
     */
    fun writeCompoundTagArray(value: Array<CompoundBinaryTag>, version: Version) {
        writeVarInt(value.size)
        for (i in value) {
            writeCompoundTag(i, version)
        }
    }

    /**
     * reads a minecraft namespacedKey from the buffer.
     * @return the namespacedKey read from the buffer
     */
    fun readNamespacedKey(): NamespacedKey {
        val full = readString()
        val separator = full.indexOf(':')
        return if (separator >= 0) {
            NamespacedKey(full.substring(0, separator), full.substring(separator + 1))
        } else {
            NamespacedKey.minecraft(full)
        }
    }

    /**
     * writes a minecraft namespacedKey to the buffer.
     * @param value the namespacedKey to write to the buffer
     */
    fun writeNamespacedKey(value: NamespacedKey) {
        writeString(value.toString())
    }

    /**
     * reads a minecraft namespacedKey array from the buffer.
     * @return the namespacedKey array read from the buffer
     */
    fun readNamespacedKeyArray(): Array<NamespacedKey> {
        val length = readVarInt()
        // Array initializer reads sequentially; avoids an invalid empty-key placeholder.
        return Array(length) { readNamespacedKey() }
    }

    /**
     * writes a minecraft namespacedKey array to the buffer.
     * @param value the namespacedKey array to write to the buffer
     */
    fun writeNamespacedKeyArray(value: Array<NamespacedKey>) {
        writeVarInt(value.size)
        for (i in value) {
            writeNamespacedKey(i)
        }
    }

    /**
     * reads a minecraft component from the buffer.
     *
     * Components are JSON strings before [Version.MINECRAFT_1_20_3] and NBT from
     * 1.20.3 onward.
     *
     * @param version the protocol version being read for
     * @return the component read from the buffer
     */
    fun readComponent(version: Version): Component {
        if (version.isAtLeast(Version.MINECRAFT_1_20_3)) {
            val json = tagToJson(readNamelessCompound())
            return GsonComponentSerializer.gson().deserializeFromTree(json)
        }
        return GsonComponentSerializer.gson().deserialize(readString())
    }

    /**
     * Reads a nameless network compound (type id + payload, no root name) as written
     * by [writeNamelessCompound]. adventure's reader expects the named form, so the
     * 2-byte empty-name length is spliced back in after the type id.
     */
    private fun readNamelessCompound(): CompoundBinaryTag {
        val id = buf.readByte()
        // Splice the 0-length name back in after the type id, then read via DataInput so
        // adventure consumes exactly this tag rather than buffering the whole buffer.
        val header = ByteArrayInputStream(byteArrayOf(id, 0, 0))
        val stream = DataInputStream(SequenceInputStream(header, ByteBufInputStream(buf)))
        return BinaryTagIO.reader().read(stream as DataInput)
    }

    /**
     * writes a minecraft component to the buffer.
     *
     * Components are JSON strings before [Version.MINECRAFT_1_20_3] and NBT from
     * 1.20.3 onward.
     *
     * @param value the component to write to the buffer
     * @param version the protocol version being written for
     */
    fun writeComponent(value: Component, version: Version) {
        if (version.isAtLeast(Version.MINECRAFT_1_20_3)) {
            // adventure ships no NBT component serializer, so bridge via its gson tree:
            // component trees always have an object root, hence a CompoundBinaryTag.
            val tree = GsonComponentSerializer.gson().serializeToTree(value)
            writeCompoundTag(jsonToTag(tree) as CompoundBinaryTag, version)
        } else {
            writeString(GsonComponentSerializer.gson().serialize(value))
        }
    }

    /**
     * reads a minecraft enumSet from the buffer.
     * @return the enumSet read from the buffer
     */
    fun <E : Enum<E>> readEnumSet(enumClass: Class<E>): Set<E> {
        val length = readVarInt()
        val enumSet = mutableSetOf<E>()
        for (i in 0 until length) {
            val enumValue = readString()
            enumSet.add(valueOf(enumClass, enumValue))
        }
        return enumSet
    }

    /**
     * writes a minecraft enumSet to the buffer.
     * @param value the enumSet to write to the buffer
     */
    fun <E : Enum<E>> writeEnumSet(value: Set<E>) {
        writeVarInt(value.size)
        for (i in value) {
            writeString(i.name)
        }
    }

    /**
     * reads a minecraft array from the buffer.
     * @return the minecraft array read from the buffer
     */
    fun readArray(): ByteArray {
        return readArray(this.buf.readableBytes())
    }

    /**
     * reads a minecraft array from the buffer with a maximum length.
     * @param maxLength the maximum length of the array to read
     * @return the minecraft array read from the buffer
     */
    fun readArray(maxLength: Int): ByteArray {
        val length = readVarInt()
        if (length > maxLength) {
            throw MyceliumReadException("Array length is too long: $length > $maxLength (got length $length)")
        }
        val array = ByteArray(length)
        buf.readBytes(array)
        return array
    }

    /**
     * Converts a gson JSON tree to its NBT equivalent for the component wire format.
     */
    private fun jsonToTag(element: JsonElement): BinaryTag = when {
        element.isJsonObject -> CompoundBinaryTag.builder().apply {
            element.asJsonObject.entrySet().forEach { (key, value) -> put(key, jsonToTag(value)) }
        }.build()
        element.isJsonArray -> ListBinaryTag.from(element.asJsonArray.map { jsonToTag(it) })
        element.isJsonPrimitive -> {
            val primitive = element.asJsonPrimitive
            when {
                primitive.isBoolean -> ByteBinaryTag.byteBinaryTag(if (primitive.asBoolean) 1 else 0)
                primitive.isNumber -> numberToTag(primitive)
                else -> StringBinaryTag.stringBinaryTag(primitive.asString)
            }
        }
        else -> throw MyceliumWriteException("Cannot convert JSON null to NBT")
    }

    private fun numberToTag(primitive: JsonPrimitive): BinaryTag {
        val number = primitive.asDouble
        return if (number == floor(number) && !number.isInfinite()) {
            IntBinaryTag.intBinaryTag(primitive.asInt)
        } else {
            DoubleBinaryTag.doubleBinaryTag(number)
        }
    }

    /** Reverse of [jsonToTag]; see its note on the byte-as-boolean mapping. */
    private fun tagToJson(tag: BinaryTag): JsonElement = when (tag) {
        is CompoundBinaryTag -> JsonObject().apply {
            tag.keySet().forEach { key -> add(key, tagToJson(tag.get(key)!!)) }
        }
        is ListBinaryTag -> JsonArray().apply { tag.forEach { add(tagToJson(it)) } }
        else -> primitiveTagToJson(tag)
    }

    private fun primitiveTagToJson(tag: BinaryTag): JsonElement = when (tag) {
        is StringBinaryTag -> JsonPrimitive(tag.value())
        is ByteBinaryTag -> JsonPrimitive(tag.value() != 0.toByte())
        is ShortBinaryTag -> JsonPrimitive(tag.value())
        is IntBinaryTag -> JsonPrimitive(tag.value())
        is LongBinaryTag -> JsonPrimitive(tag.value())
        is FloatBinaryTag -> JsonPrimitive(tag.value())
        is DoubleBinaryTag -> JsonPrimitive(tag.value())
        else -> throw MyceliumReadException("Unsupported NBT tag for component: ${tag.type()}")
    }

    companion object {
        private const val SEGMENT_BITS = 0x7F
        private const val CONTINUE_BIT = 0x80
        private const val SEGMENT_SHIFT = 7
        private const val MAX_VARINT_BYTES = 5
        private const val MAX_BYTES_PER_CHAR = 3
        private const val NAMED_ROOT_HEADER_LENGTH = 3 // TAG_Compound id + 2-byte empty name
    }
}
