package lol.simeon.mycelium

/**
 * Minecraft protocol versions relevant to serialization behaviour.
 *
 * Entries cover the protocol-distinct releases from 1.8 upward; comparison is by
 * [protocol] number, so a client on an in-between point release resolves through
 * [isAtLeast] without needing its own entry. Add a specific release only when a
 * serialization quirk actually branches on it.
 *
 * @property protocol the network protocol number, or -1 for [UNDEFINED]
 */
enum class Version(val protocol: Int) {
    UNDEFINED(-1),
    MINECRAFT_1_8(47),
    MINECRAFT_1_9(107),
    MINECRAFT_1_10(210),
    MINECRAFT_1_11(315),
    MINECRAFT_1_12(335),
    MINECRAFT_1_12_2(340), // Keep Alive payload switched VarInt -> Long here
    MINECRAFT_1_13(393),
    MINECRAFT_1_14(477),
    MINECRAFT_1_15(573),
    MINECRAFT_1_16(735),
    MINECRAFT_1_16_2(751),
    MINECRAFT_1_17(755),
    MINECRAFT_1_18(757),
    MINECRAFT_1_18_2(758),
    MINECRAFT_1_19(759),
    MINECRAFT_1_19_1(760),
    MINECRAFT_1_19_3(761),
    MINECRAFT_1_19_4(762),
    MINECRAFT_1_20(763),
    MINECRAFT_1_20_2(764),
    MINECRAFT_1_20_3(765),
    MINECRAFT_1_20_5(766),
    MINECRAFT_1_21(767),
    MINECRAFT_1_21_2(768),
    MINECRAFT_1_21_4(769),
    MINECRAFT_1_21_5(770),
    MINECRAFT_1_21_6(771),
    MINECRAFT_1_21_7(772),
    MINECRAFT_1_21_9(773),
    MINECRAFT_1_21_11(774),
    MINECRAFT_26_1(775),
    MINECRAFT_26_2(776),
    MINECRAFT_26_3(777),
    ;

    /** @return true if this version is [other] or newer. */
    fun isAtLeast(other: Version): Boolean = protocol >= other.protocol
}
