/*
 * MIT License
 *
 * Copyright (c) 2020 Melms Media LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 From Octave bot https://github.com/Stardust-Discord/Octave/ Modified for integrating with JAVA and the current bot
 */
package core.commands.music.dj;

import core.Chuu;
import core.commands.abstracts.MusicCommand;
import core.exceptions.LastFmException;
import core.music.MusicManager;
import core.parsers.NoOpParser;
import core.parsers.Parser;
import core.parsers.params.CommandParameters;
import dao.ChuuService;
import dao.exceptions.InstanceNotFoundException;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import javax.validation.constraints.NotNull;
import java.util.List;

public class StopCommand extends MusicCommand<CommandParameters> {
    public StopCommand(ChuuService dao) {
        super(dao);
    }

    @Override
    public Parser<CommandParameters> initParser() {
        return new NoOpParser();
    }

    @Override
    public String getDescription() {
        return "Stops and clears the music player";
    }

    @Override
    public List<String> getAliases() {
        return List.of("end", "st", "fuckoff");
    }

    @Override
    public String getName() {
        return "Stop music";
    }

    @Override
    protected void onCommand(MessageReceivedEvent e, @NotNull CommandParameters params) throws LastFmException, InstanceNotFoundException {
        MusicManager manager = getManager(e);
        manager.setRadio(null);
        manager.getQueue().clear();
        e.getGuild().getAudioManager().closeAudioConnection();
        Chuu.playerRegistry.destroy(e.getGuild().getIdLong());
        sendMessageQueue(e, ("Playback has been completely stopped."));
    }
}
