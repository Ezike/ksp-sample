@Function(name = "myAmazingFunction")
interface MyAmazingFunction {
    val name: String
    val args: Map<String, List<*>>
    @Returns
    val end: Pair<String, Boolean>
}

@Function(name = "new")
interface MyNewFunction

fun main() {
    myAmazingFunction("some funct", mapOf("as" to listOf("asiri")), Pair("", false))
    new()
}