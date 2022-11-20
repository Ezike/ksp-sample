import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import java.io.OutputStream
import java.lang.StringBuilder

class FunctionProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver
            .getSymbolsWithAnnotation("Function")
            .filterIsInstance<KSClassDeclaration>()

        if (!symbols.iterator().hasNext()) return emptyList()

        val file: OutputStream = codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false, sources = resolver.getAllFiles().toList().toTypedArray()),
            packageName = "",
            fileName = "GeneratedFunctions"
        )

        symbols.forEach { it.accept(Visitor(file), Unit) }

        file.close()

        return symbols.filterNot { it.validate() }.toList()
    }

    operator fun OutputStream.plusAssign(str: String) {
        this.write(str.toByteArray())
    }

    inner class Visitor(private val file: OutputStream) : KSVisitorVoid() {

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            if (classDeclaration.classKind != ClassKind.INTERFACE) {
                logger.error("Only interfaces can be annotated with @Function", classDeclaration)
                return
            }

            // Getting the @Function annotation object.
            val annotation: KSAnnotation = classDeclaration.annotations.first {
                it.shortName.asString() == "Function"
            }

            // Getting the 'name' argument object from the @Function.
            val nameArgument: KSValueArgument = annotation.arguments.first {
                it.name?.asString() == "name"
            }

            // Getting the value of the 'name' argument.
            val functionName = nameArgument.value as String

            // Getting the list of member properties of the annotated interface.
            val properties: Sequence<KSPropertyDeclaration> = classDeclaration.getAllProperties()
                .filter { it.validate() }

            // Generating function signature.
            file += "\n"

            val stringBuilder = StringBuilder()
            if (properties.iterator().hasNext()) {
                file += "fun $functionName(\n"

                // Iterating through each property to translate them to function arguments.
                properties.forEach { property ->
                    visitPropertyDeclaration(property, Unit)
                    stringBuilder
                        .append("    println(\"")
                        .append("$${property.simpleName.asString()}")
                        .appendLine("\")")
                }
                properties.flatMap { it.annotations.filter { it.shortName.asString() == "Returns" } }
                    .count()
                    .let { if (it > 1) error("Can't use @Returns on more than one property") }

                val prop = properties.firstOrNull { it.annotations.any { it.shortName.asString() == "Returns" } }

                if (prop != null) {
                    val returnTypeName = getReturnType(prop)
                    file += "): $returnTypeName {\n"
                    stringBuilder.append("    return ${prop.simpleName.asString()}\n")
                } else {
                    file += ") {\n"
                }
            } else {
                // Otherwise, generating function with no args.
                file += "fun $functionName() {\n"
                stringBuilder.append("    println(\"Hello from $functionName\")\n")
            }

            // Generating function body
            file += "$stringBuilder"
            file += "}\n"
        }

        private fun getReturnType(prop: KSPropertyDeclaration): String {
            val resolve = prop.type.resolve()
            val returnTypeName = resolve.declaration.qualifiedName?.asString() ?: run {
                logger.error("Invalid property type", prop)
                return ""
            }
            val typeArgs = prop.type.element?.typeArguments ?: return returnTypeName
            val stringBuilder = StringBuilder()
            visitTypeArgs(typeArgs, stringBuilder)
            return "$returnTypeName$stringBuilder"
        }

        override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit) {
            val argumentName = property.simpleName.asString()

            file += "    $argumentName: "

            val resolvedType: KSType = property.type.resolve()

            file += resolvedType.declaration.qualifiedName?.asString() ?: run {
                logger.error("Invalid property type", property)
                return
            }

            val genericArgs = property.type.element?.typeArguments ?: emptyList()
            visitTypeArguments(genericArgs)

            // Handling nullability
            if (resolvedType.nullability == Nullability.NULLABLE) file += "?"

            file += ",\n"
        }

        override fun visitTypeArgument(typeArgument: KSTypeArgument, data: Unit) {
            if (options["ignoreGenericArgs"] == "true") {
                file += "*"
                return
            }

            when (val variance: Variance = typeArgument.variance) {
                Variance.STAR -> {
                    file += "*"
                    return
                }

                Variance.COVARIANT, Variance.CONTRAVARIANT -> {
                    file += variance.label
                    file += " "
                }

                Variance.INVARIANT -> {
                    // no-op
                }
            }

            val resolvedType: KSType = typeArgument.type?.resolve() ?: return
            file += resolvedType.declaration.qualifiedName?.asString() ?: run {
                logger.error("Invalid type argument", typeArgument)
                return
            }

            // Generating nested generic parameters if any (eg: List<List<*>>)
            val genericArgs = typeArgument.type?.element?.typeArguments ?: emptyList()
            visitTypeArguments(genericArgs)

            // Handling nullability
            if (resolvedType.nullability == Nullability.NULLABLE) file += "?"
        }

        private fun visitTypeArguments(typeArguments: List<KSTypeArgument>) {
            if (typeArguments.isNotEmpty()) {
                file += "<"
                typeArguments.forEachIndexed { index, ksTypeArgument ->
                    visitTypeArgument(ksTypeArgument, Unit)
                    if (index < typeArguments.lastIndex) file += ", "
                }
                file += ">"
            }
        }

        private fun visitTypeArgs(typeArguments: List<KSTypeArgument>, builder: StringBuilder) {
            if (typeArguments.isEmpty()) return
            fun visit(typeArgument: KSTypeArgument) {
                if (options["ignoreGenericArgs"] == "true") {
                    builder.append("*")
                    return
                }

                when (val variance: Variance = typeArgument.variance) {
                    Variance.STAR -> {
                        builder.append("*")
                        return
                    }

                    Variance.COVARIANT, Variance.CONTRAVARIANT -> {
                        builder.append(variance.label)
                        builder.append(" ")
                    }

                    Variance.INVARIANT -> {
                        // no-op
                    }
                }

                val resolvedType: KSType = typeArgument.type?.resolve() ?: return
                builder.append(
                    resolvedType.declaration.qualifiedName?.asString() ?: run {
                        logger.error("Invalid type argument", typeArgument)
                        return
                    }
                )

                // Generating nested generic parameters if any (eg: List<List<*>>)
                val genericArgs = typeArgument.type?.element?.typeArguments ?: emptyList()
                visitTypeArgs(genericArgs, builder)

                // Handling nullability
                if (resolvedType.nullability == Nullability.NULLABLE) file += "?"
            }

            builder.append("<")
            typeArguments.forEachIndexed { index, ksTypeArgument ->
                visit(ksTypeArgument)
                if (index < typeArguments.lastIndex) builder.append(", ")
            }
            builder.append(">")
        }
    }
}