/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:OptIn(ExperimentalStdlibApi::class)

package net.mamoe.mirai.console.command.parse

import net.mamoe.mirai.console.command.Command
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors

/**
 * Unresolved [CommandCall].
 */
@ExperimentalCommandDescriptors
public interface CommandCall {
    public val caller: CommandSender

    /**
     * One of callee [Command]'s [Command.allNames]
     */
    public val calleeName: String

    /**
     * Explicit value arguments
     */
    public val valueArguments: List<CommandValueArgument>
}

@ExperimentalCommandDescriptors
public class CommandCallImpl(
    override val caller: CommandSender,
    override val calleeName: String,
    override val valueArguments: List<CommandValueArgument>,
) : CommandCall