package lol.simeon.mycelium

/**
 * A block position, packed into a single long on the wire.
 *
 * Each component is signed: x/z are 26-bit, y is 12-bit. The field order in the
 * packed long changed in 1.14, so [ByteMessage.readPosition]/[ByteMessage.writePosition]
 * take a [Version].
 */
data class Position(val x: Int, val y: Int, val z: Int)
