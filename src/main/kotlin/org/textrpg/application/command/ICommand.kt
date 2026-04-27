package org.textrpg.application.command

import org.textrpg.application.adapter.onebot.Message

interface ICommand {
    fun execute(message: List<Message>): ICommand
}