package net.kodehawa.mantarobot.cmd;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.managers.AudioManager;
import net.kodehawa.mantarobot.audio.MusicManager;
import net.kodehawa.mantarobot.module.Callback;
import net.kodehawa.mantarobot.module.CommandType;
import net.kodehawa.mantarobot.module.Module;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class Audio extends Module {

    private final AudioPlayerManager playerManager;
    private final Map<Long, MusicManager> musicManagers;

    public Audio(){
        this.musicManagers = new HashMap<>();
        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
        this.registerCommands();
    }

    @Override
    public void registerCommands(){
        super.register("play", "Plays a song in the music voice channel.", new Callback() {
            @Override
            public void onCommand(String[] args, String content, MessageReceivedEvent event) {
                loadAndPlay(event.getTextChannel(), args[0], "");
            }

            @Override
            public String help() {
                return "";
            }

            @Override
            public CommandType commandType() {
                return CommandType.USER;
            }
        });

        super.register("skip", "Stops the track and continues to the next one, if there is one.", new Callback() {
            @Override
            public void onCommand(String[] args, String content, MessageReceivedEvent event) {
                MusicManager musicManager = musicManagers.get(Long.parseLong(event.getGuild().getId()));
                if(nextTrackAvaliable(musicManager)){
                    skipTrack(event.getTextChannel());
                }
                else {
                    event.getChannel().sendMessage("No tracks next. Disconnecting...").queue();
                    closeConnection(event.getGuild().getAudioManager(), event.getTextChannel());
                }
            }

            @Override
            public String help() {
                return "";
            }

            @Override
            public CommandType commandType() {
                return CommandType.USER;
            }
        });

        super.register("musicleave", "Leaves the voice channel.", new Callback() {
            @Override
            public void onCommand(String[] args, String content, MessageReceivedEvent event) {
                closeConnection(event.getGuild().getAudioManager(), event.getTextChannel());
            }

            @Override
            public String help() {
                return "";
            }

            @Override
            public CommandType commandType() {
                return CommandType.USER;
            }
        });

        super.register("tracklist", "Returns the current tracklist playing on the server.", new Callback() {
            @Override
            public void onCommand(String[] args, String content, MessageReceivedEvent event) {
                MusicManager musicManager = musicManagers.get(Long.parseLong(event.getGuild().getId()));
                event.getChannel().sendMessage(embedQueueList(musicManager)).queue();
            }

            @Override
            public String help() {
                return "";
            }

            @Override
            public CommandType commandType() {
                return CommandType.USER;
            }
        });
    }

    private synchronized MusicManager getGuildAudioPlayer(Guild guild) {
        long guildId = Long.parseLong(guild.getId());
        MusicManager musicManager = musicManagers.get(guildId);
        if (musicManager == null) {
            musicManager = new MusicManager(playerManager);
            musicManagers.put(guildId, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    private void loadAndPlay(final TextChannel channel, final String trackUrl, String channelName) {
        MusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                channel.sendMessage("Adding track to queue: " + track.getInfo().title).queue();
                play(channel.getGuild(), musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack firstTrack = playlist.getSelectedTrack();
                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }

                channel.sendMessage("Adding to queue " + firstTrack.getInfo().title + " (first track of playlist " + playlist.getName() + ")").queue();
                play(channel.getGuild(), musicManager, firstTrack);
            }

            @Override
            public void noMatches() {
                channel.sendMessage("Nothing found on " + trackUrl).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                channel.sendMessage("Couldn't play music: " + exception.getMessage()).queue();
            }
        });
    }

    private void play(Guild guild, MusicManager musicManager, AudioTrack track) {
        connectToFirstVoiceChannel(guild.getAudioManager());
        musicManager.getScheduler().queue(track);
    }

    private void play(String channelName, Guild guild, MusicManager musicManager, AudioTrack track) {
        connectToNamedVoiceChannel(channelName, guild.getAudioManager());
        musicManager.getScheduler().queue(track);
    }

    private void skipTrack(TextChannel channel) {
        MusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        musicManager.getScheduler().nextTrack();
        channel.sendMessage("Skipped to next track.").queue();
    }

    private static void connectToFirstVoiceChannel(AudioManager audioManager) {
        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
            for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
                audioManager.openAudioConnection(voiceChannel);
                break;
            }
        }
    }

    private static void connectToNamedVoiceChannel(String string, AudioManager audioManager){
        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
            for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
                if(voiceChannel.getName().contains(string)){
                    audioManager.openAudioConnection(voiceChannel);
                }
                break;
            }
        }
    }

    private void closeConnection(AudioManager audioManager, TextChannel channel) {
        audioManager.closeAudioConnection();
        channel.sendMessage("Closed audio connection.").queue();
    }

    private boolean nextTrackAvaliable(MusicManager musicManager){
        if(musicManager.getScheduler().getQueueSize() > 0){
            return true;
        }
        return false;
    }

    private MessageEmbed embedQueueList(MusicManager musicManager) {
        String toSend = musicManager.getScheduler().getQueueList();
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("Track list.");
        builder.setColor(Color.CYAN);
        builder.setDescription(toSend);

        return builder.build();
    }
}