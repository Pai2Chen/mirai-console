/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("NOTHING_TO_INLINE", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "unused", "MemberVisibilityCanBePrivate")

package net.mamoe.mirai.console.command.descriptor

import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.CommandArgumentContext.ParserPair
import net.mamoe.mirai.console.permission.PermissionId
import net.mamoe.mirai.console.permission.PermitteeId
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.MessageContent
import net.mamoe.mirai.message.data.PlainText
import kotlin.internal.LowPriorityInOverloadResolution
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf


/**
 * 指令参数环境, 即 [CommandValueArgumentParser] 的集合, 用于 [CompositeCommand] 和 [SimpleCommand].
 *
 * 在指令解析时, 总是从 [CommandArgumentContextAware.context] 搜索相关解析器
 *
 * 要构造 [CommandArgumentContext], 参考 [buildCommandArgumentContext]
 *
 * @see SimpleCommandArgumentContext 简单实现
 * @see EmptyCommandArgumentContext 空实现, 类似 [emptyList]
 *
 * @see CommandArgumentContext.Builtins 内建 [CommandValueArgumentParser]
 *
 * @see buildCommandArgumentContext DSL 构造
 */
public interface CommandArgumentContext {
    /**
     * [KClass] 到 [CommandValueArgumentParser] 的匹配
     */
    public data class ParserPair<T : Any>(
        val klass: KClass<T>,
        val parser: CommandValueArgumentParser<T>,
    )

    public operator fun <T : Any> get(klass: KClass<out T>): CommandValueArgumentParser<T>?

    public fun toList(): List<ParserPair<*>>

    public companion object {
        /**
         * For Java callers.
         *
         * @see [EmptyCommandArgumentContext]
         */
        @JvmStatic
        public val EMPTY: CommandArgumentContext = EmptyCommandArgumentContext
    }

    /**
     * 内建的默认 [CommandValueArgumentParser]
     */
    public object Builtins : CommandArgumentContext by (buildCommandArgumentContext {
        Int::class with IntValueArgumentParser
        Byte::class with ByteValueArgumentParser
        Short::class with ShortValueArgumentParser
        Boolean::class with BooleanValueArgumentParser
        String::class with StringValueArgumentParser
        Long::class with LongValueArgumentParser
        Double::class with DoubleValueArgumentParser
        Float::class with FloatValueArgumentParser

        Image::class with ImageValueArgumentParser
        PlainText::class with PlainTextValueArgumentParser

        Contact::class with ExistingContactValueArgumentParser
        User::class with ExistingUserValueArgumentParser
        Member::class with ExistingMemberValueArgumentParser
        Group::class with ExistingGroupValueArgumentParser
        Friend::class with ExistingFriendValueArgumentParser
        Bot::class with ExistingBotValueArgumentParser

        PermissionId::class with PermissionIdValueArgumentParser
        PermitteeId::class with PermitteeIdValueArgumentParser

        MessageContent::class with RawContentValueArgumentParser
    })
}

/**
 * 拥有 [buildCommandArgumentContext] 的类
 *
 * @see SimpleCommand
 * @see CompositeCommand
 */
public interface CommandArgumentContextAware {
    /**
     * [CommandValueArgumentParser] 的集合
     */
    public val context: CommandArgumentContext
}

public object EmptyCommandArgumentContext : CommandArgumentContext by SimpleCommandArgumentContext(listOf())

/**
 * 合并两个 [buildCommandArgumentContext], [replacer] 将会替换 [this] 中重复的 parser.
 */
public operator fun CommandArgumentContext.plus(replacer: CommandArgumentContext): CommandArgumentContext {
    if (replacer == EmptyCommandArgumentContext) return this
    if (this == EmptyCommandArgumentContext) return replacer
    return object : CommandArgumentContext {
        override fun <T : Any> get(klass: KClass<out T>): CommandValueArgumentParser<T>? =
            replacer[klass] ?: this@plus[klass]

        override fun toList(): List<ParserPair<*>> = replacer.toList() + this@plus.toList()
    }
}

/**
 * 合并 [this] 与 [replacer], [replacer] 将会替换 [this] 中重复的 parser.
 */
public operator fun CommandArgumentContext.plus(replacer: List<ParserPair<*>>): CommandArgumentContext {
    if (replacer.isEmpty()) return this
    if (this == EmptyCommandArgumentContext) return SimpleCommandArgumentContext(replacer)
    return object : CommandArgumentContext {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> get(klass: KClass<out T>): CommandValueArgumentParser<T>? =
            replacer.firstOrNull { klass.isSubclassOf(it.klass) }?.parser as CommandValueArgumentParser<T>?
                ?: this@plus[klass]

        override fun toList(): List<ParserPair<*>> = replacer.toList() + this@plus.toList()
    }
}

/**
 * 自定义 [buildCommandArgumentContext]
 *
 * @see buildCommandArgumentContext
 */
@Suppress("UNCHECKED_CAST")
public class SimpleCommandArgumentContext(
    public val list: List<ParserPair<*>>,
) : CommandArgumentContext {
    override fun <T : Any> get(klass: KClass<out T>): CommandValueArgumentParser<T>? =
        (this.list.firstOrNull { klass == it.klass }?.parser
            ?: this.list.firstOrNull { klass.isSubclassOf(it.klass) }?.parser) as CommandValueArgumentParser<T>?

    override fun toList(): List<ParserPair<*>> = list
}

/**
 * 构建一个 [buildCommandArgumentContext].
 *
 * Kotlin 实现:
 * ```
 * val context = buildCommandArgumentContext {
 *     Int::class with IntArgParser
 *     Member::class with ExistingMemberArgParser
 *     Group::class with { s: String, sender: CommandSender ->
 *          Bot.getInstance(s.toLong()).getGroup(s.toLong())
 *     }
 *     Bot::class with { s: String ->
 *          Bot.getInstance(s.toLong())
 *     }
 * }
 * ```
 *
 * Java 实现:
 * ```java
 * CommandArgumentContext context =
 *     new CommandArgumentContextBuilder()
 *         .add(clazz1, parser1)
 *         .add(String.class, new CommandArgumentParser<String>() {
 *              public String parse(String raw, CommandSender sender) {
 *                  // ...
 *              }
 *         })
 *         // 更多 add
 *         .build()
 * ```
 *
 * @see CommandArgumentContextBuilder
 * @see buildCommandArgumentContext
 */
@JvmSynthetic
public fun buildCommandArgumentContext(block: CommandArgumentContextBuilder.() -> Unit): CommandArgumentContext {
    return CommandArgumentContextBuilder().apply(block).build()
}

/**
 * 参考 [buildCommandArgumentContext]
 */
public class CommandArgumentContextBuilder : MutableList<ParserPair<*>> by mutableListOf() {
    /**
     * 添加一个指令解析器.
     */
    @JvmName("add")
    public infix fun <T : Any> Class<T>.with(parser: CommandValueArgumentParser<T>): CommandArgumentContextBuilder =
        this.kotlin with parser

    /**
     * 添加一个指令解析器
     */
    @JvmName("add")
    public inline infix fun <T : Any> KClass<T>.with(parser: CommandValueArgumentParser<T>): CommandArgumentContextBuilder {
        add(ParserPair(this, parser))
        return this@CommandArgumentContextBuilder
    }

    /**
     * 添加一个指令解析器
     */
    @JvmSynthetic
    @LowPriorityInOverloadResolution
    public inline infix fun <T : Any> KClass<T>.with(
        crossinline parser: CommandValueArgumentParser<T>.(s: String, sender: CommandSender) -> T,
    ): CommandArgumentContextBuilder {
        add(ParserPair(this, object : CommandValueArgumentParser<T> {
            override fun parse(raw: String, sender: CommandSender): T = parser(raw, sender)
        }))
        return this@CommandArgumentContextBuilder
    }

    /**
     * 添加一个指令解析器
     */
    @JvmSynthetic
    public inline infix fun <T : Any> KClass<T>.with(
        crossinline parser: CommandValueArgumentParser<T>.(s: String) -> T,
    ): CommandArgumentContextBuilder {
        add(ParserPair(this, object : CommandValueArgumentParser<T> {
            override fun parse(raw: String, sender: CommandSender): T = parser(raw)
        }))
        return this@CommandArgumentContextBuilder
    }

    @JvmSynthetic
    public inline fun <reified T : Any> add(parser: CommandValueArgumentParser<T>): CommandArgumentContextBuilder {
        add(ParserPair(T::class, parser))
        return this@CommandArgumentContextBuilder
    }

    /**
     * 添加一个指令解析器
     */
    @ConsoleExperimentalApi
    @JvmSynthetic
    public inline infix fun <reified T : Any> add(
        crossinline parser: CommandValueArgumentParser<*>.(s: String) -> T,
    ): CommandArgumentContextBuilder = T::class with object : CommandValueArgumentParser<T> {
        override fun parse(raw: String, sender: CommandSender): T = parser(raw)
    }

    /**
     * 添加一个指令解析器
     */
    @ConsoleExperimentalApi
    @JvmSynthetic
    @LowPriorityInOverloadResolution
    public inline infix fun <reified T : Any> add(
        crossinline parser: CommandValueArgumentParser<*>.(s: String, sender: CommandSender) -> T,
    ): CommandArgumentContextBuilder = T::class with object : CommandValueArgumentParser<T> {
        override fun parse(raw: String, sender: CommandSender): T = parser(raw, sender)
    }

    /**
     * 完成构建, 得到 [CommandArgumentContext]
     */
    public fun build(): CommandArgumentContext = SimpleCommandArgumentContext(this.distinctByReversed { it.klass })
}

internal inline fun <T, K> List<T>.distinctByReversed(selector: (T) -> K): List<T> {
    val set = HashSet<K>()
    val list = ArrayList<T>()
    for (i in this.indices.reversed()) {
        val element = this[i]
        if (set.add(element.let(selector))) {
            list.add(element)
        }
    }
    return list
}