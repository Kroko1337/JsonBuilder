package me.krokoyt.json.api

object ArgumentParser {
    private val arguments = HashMap<String, ArrayList<String>>()

    fun getArgument(name: String): ArrayList<String> {
        return arguments[name] ?: throw Exception("Argument $name not found!")
    }

    fun getArgumentUnsafe(name: String): ArrayList<String>? {
        return arguments[name]
    }

    fun parse(args: Array<String>) {
        val array = ArrayList<String>()
        var currentArg = ""
        for (i in args.indices) {
            val arg = args[i]
            if (arg.startsWith("--")) {
                if (currentArg.isNotBlank())
                    arguments[currentArg] = ArrayList(array)
                array.clear()
                currentArg = arg.substring(2)
            } else {
                array.add(arg)
            }

            if(i == args.size - 1) {
                if (currentArg.isNotBlank())
                    arguments[currentArg] = ArrayList(array)
                array.clear()
            }
        }
    }
}