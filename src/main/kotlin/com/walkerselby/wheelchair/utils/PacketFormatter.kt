package com.walkerselby.wheelchair.utils

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.Packet
import net.minecraft.text.Text
import java.lang.reflect.Field
import java.lang.reflect.Modifier

val Field.isStatic: Boolean
    get() = (modifiers and Modifier.STATIC) != 0

interface Printer {
    fun emitName(value: String)
    fun emitValue(value: String)
    fun beginObject(label: String): Printer
    fun beginList(label: String): Printer
}

class AccumulatingPrinter(
    val indent: Int,
    val stringBuilder: StringBuilder,
    val isListBuilder: Boolean,
) : Printer {

    fun appendIndent() {
        stringBuilder.append(" ".repeat(indent))
    }

    override fun emitName(value: String) {
        if (isListBuilder) {
            error("Cannot print name within a list")
        }
        appendIndent()
        stringBuilder.append(value)
        stringBuilder.append(": ")
    }

    override fun emitValue(value: String) {
        if (isListBuilder)
            stringBuilder.append("* ")
        else
            stringBuilder.append("")
        stringBuilder.append(value)
        stringBuilder.append("\n")
    }

    override fun beginObject(label: String): Printer {
        if (isListBuilder) {
            stringBuilder.append("* $label:\n")
        } else {
            stringBuilder.append("$label\n")
        }
        return AccumulatingPrinter(indent + 2, stringBuilder, false)
    }

    override fun beginList(label: String): Printer {
        if (isListBuilder) {
            stringBuilder.append("* $label\n")
        } else {
            stringBuilder.append("$label:\n")
        }
        return AccumulatingPrinter(indent + 2, stringBuilder, true)
    }

}

fun interface Formatter<T> {

    fun prettyPrint(subject: T, printer: Printer)

    companion object {
        private val formatters = mutableMapOf<Class<*>, Formatter<*>>()
        private val dynamicFormatter = mutableListOf<(Class<*>) -> Formatter<*>?>()
        private val processing = mutableSetOf<Class<*>>()

        fun <V> putFormatter(clazz: Class<V>, formatter: Formatter<V>) {
            formatters[clazz] = formatter
        }

        fun <V> putFormatter(function: (Class<in V>) -> Formatter<V>?) {
            dynamicFormatter.add(function as ((Class<*>) -> Formatter<*>))
        }

        init {
            putFormatter(
                Integer.TYPE
            ) { subject, printer -> printer.emitValue(subject.toString()) }
            putFormatter(
                Integer::class.java
            ) { subject, printer -> printer.emitValue(subject.toString()) }
            putFormatter(
                String::class.java
            ) { subject, printer -> printer.emitValue(subject) }
            putFormatter(
                ItemStack::class.java
            ) { subject, printer -> printer.emitValue("${subject.count}x${subject.name.string}§r") }
            putFormatter {
                if (Int2ObjectMap::class.java.isAssignableFrom(it)) {
                    return@putFormatter object : Formatter<Int2ObjectMap<*>> {
                        override fun prettyPrint(subject: Int2ObjectMap<*>, printer: Printer) {
                            val child = printer.beginObject("Int2ObjectMap (length=${subject.size})")
                            subject.forEach { (k, v) ->
                                val valueFormatter = getPrettyPrinter(v.javaClass)
                                child.emitName(k.toString())
                                valueFormatter.prettyPrint(v, child)
                            }
                        }
                    }
                }
                return@putFormatter null
            }
        }

        class LazyFormatter<T>(val clazz: Class<T>) : Formatter<T> {
            override fun prettyPrint(subject: T, printer: Printer) {
                (formatters[clazz] as Formatter<T>).prettyPrint(subject, printer)
            }
        }

        fun prettyPrintPacket(packet: Packet<*>): Text {
            val sb = StringBuilder()
            getPrettyPrinter(packet.javaClass).prettyPrint(packet, AccumulatingPrinter(0, sb, false))
            return Text.literal(sb.toString())
        }

        fun <T> getPrettyPrinter(clazz: Class<T>): Formatter<T> {
            if (clazz in processing) {
                return LazyFormatter(clazz)
            }
            if (clazz.isEnum) {
                return object : Formatter<T> {
                    override fun prettyPrint(subject: T, printer: Printer) {
                        printer.emitValue(subject.toString())
                    }
                }
            }
            return formatters.getOrPut(clazz) {
                try {
                    processing.add(clazz)

                    val dynamic = dynamicFormatter.firstNotNullOfOrNull { dynamic -> dynamic(clazz) }
                    if (dynamic != null) {
                        return@getOrPut dynamic
                    }

                    val fields = clazz.declaredFields
                        .filter { !it.isStatic }
                        .onEach { it.isAccessible = true }
                        .associateWith { getPrettyPrinter(it.type) }
                    val name = clazz.simpleName // TODO: remapping
                    object : Formatter<T> {
                        override fun prettyPrint(subject: T, printer: Printer) {
                            val child = printer.beginObject(name)
                            fun <V> printField(value: V, formatter: Formatter<V>) {
                                formatter.prettyPrint(value, child)
                            }
                            for ((field, fieldFormatter) in fields) {
                                val value = field.get(subject)
                                child.emitName(field.name) // TODO: remapping
                                // We love `Nothing` being unable to be cast away
                                printField(value as Any, fieldFormatter as Formatter<Any>)
                            }
                        }
                    }
                } finally {
                    processing.remove(clazz)
                }
            } as Formatter<T>
        }

    }
}

