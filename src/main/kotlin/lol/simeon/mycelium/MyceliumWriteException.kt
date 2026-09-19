package lol.simeon.mycelium

class MyceliumWriteException: RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
