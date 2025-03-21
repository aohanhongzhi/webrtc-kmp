expect class BoschSocket {

    fun id(): String

    fun on(event: String, callback: (args: Array<Any?>) -> Unit)

    fun connect()

    fun disconnect()

    fun emit(event: String, vararg args: Any?)
//    fun emit(event: String, args: Array<Any?>)

}
