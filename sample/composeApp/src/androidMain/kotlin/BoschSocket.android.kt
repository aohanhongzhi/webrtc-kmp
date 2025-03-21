actual class BoschSocket {
    actual fun id(): String {
        return "1"
    }

    actual fun on(event: String, callback: (args: Array<Any?>) -> Unit) {
    }

    actual fun connect() {
    }

    actual fun disconnect() {
    }

    actual fun emit(event: String, vararg args: Any?) {
    }

}
