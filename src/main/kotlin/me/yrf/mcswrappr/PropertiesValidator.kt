package me.yrf.mcswrappr

import org.springframework.stereotype.Component
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Paths

@Component
class PropertiesValidator : Validator {
    override fun supports(clazz: Class<*>?): Boolean {
        return clazz?.isAssignableFrom(WrapperProperties::class.java) ?: false
    }

    override fun validate(target: Any?, errors: Errors?) {
        val props = target as WrapperProperties
        try {
            val jarPath = Paths.get(props.jarPath)
            if (Files.notExists(jarPath))
                errors?.rejectValue("jarPath", "BP001", "Non existant path")
            props.jarPath = jarPath.toAbsolutePath().toString()
            val javaPath = Paths.get(props.javaPath)
            if (Files.notExists(javaPath))
                errors?.rejectValue("javaPath", "BP001", "Non existant path")
            props.javaPath = javaPath.toAbsolutePath().toString()
            props.jvmArgs = props.jvmArgs.filter { !it.contains(Regex("-xm[xs]", RegexOption.IGNORE_CASE)) }

            if (!props.minMemory.matches(Regex("[\\d]+[KMG]", RegexOption.IGNORE_CASE)))
                errors?.rejectValue("minMemory", "BM001", "Invalid memory size")
            if (!props.maxMemory.matches(Regex("[\\d]+[KMG]", RegexOption.IGNORE_CASE)))
                errors?.rejectValue("maxMemory", "BM001", "Invalid memory size")
        } catch (ex: InvalidPathException) {
            errors?.reject("BP000", "Invalid Path in config.")
        }
    }

}