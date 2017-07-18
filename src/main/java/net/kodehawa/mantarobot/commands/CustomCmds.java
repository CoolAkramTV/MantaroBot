package net.kodehawa.mantarobot.commands;

import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.ISnowflake;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.kodehawa.dataporter.oldentities.OldCustomCommand;
import net.kodehawa.mantarobot.MantaroBot;
import net.kodehawa.mantarobot.commands.currency.TextChannelGround;
import net.kodehawa.mantarobot.commands.custom.ConditionalCustoms;
import net.kodehawa.mantarobot.commands.custom.EmbedJSON;
import net.kodehawa.mantarobot.core.listeners.command.CommandListener;
import net.kodehawa.mantarobot.core.listeners.operations.InteractiveOperations;
import net.kodehawa.mantarobot.core.listeners.operations.Operation;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.modules.CommandRegistry;
import net.kodehawa.mantarobot.modules.Module;
import net.kodehawa.mantarobot.modules.PostLoadEvent;
import net.kodehawa.mantarobot.modules.commands.CommandPermission;
import net.kodehawa.mantarobot.modules.commands.SimpleCommand;
import net.kodehawa.mantarobot.modules.commands.base.AbstractCommand;
import net.kodehawa.mantarobot.modules.commands.base.Category;
import net.kodehawa.mantarobot.utils.DiscordUtils;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;
import net.kodehawa.mantarobot.utils.data.GsonDataManager;
import org.apache.commons.lang3.tuple.Pair;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static br.com.brjdevs.java.utils.collections.CollectionUtils.random;
import static net.kodehawa.mantarobot.commands.custom.Mapifier.dynamicResolve;
import static net.kodehawa.mantarobot.commands.custom.Mapifier.map;
import static net.kodehawa.mantarobot.commands.info.CommandStatsManager.log;
import static net.kodehawa.mantarobot.commands.info.HelpUtils.forType;
import static net.kodehawa.mantarobot.data.MantaroData.db;
import static net.kodehawa.mantarobot.utils.StringUtils.SPLIT_PATTERN;

@Slf4j
@Module
public class CustomCmds {
	private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_]+"),
		INVALID_CHARACTERS_PATTERN = Pattern.compile("[^a-zA-Z0-9_]"),
		NAME_WILDCARD_PATTERN = Pattern.compile("[a-zA-Z0-9_*]+");
	private static Map<String, List<String>> customCommands = new ConcurrentHashMap<>();
	private static final net.kodehawa.mantarobot.modules.commands.base.Command customCommand = new AbstractCommand(null) {
		@Override
		public MessageEmbed help(GuildMessageReceivedEvent event) {
			return null;
		}

		private void handle(String cmdName, GuildMessageReceivedEvent event) {
			List<String> values = customCommands.get(event.getGuild().getId() + ":" + cmdName);

			if (values == null) return;

			String response = random(values);

			if (response.contains("$(")) {
				Map<String, String> dynamicMap = new HashMap<>();
				map("event", dynamicMap, event);
				response = dynamicResolve(response, dynamicMap);
			}

			response = ConditionalCustoms.resolve(response, 0);

			int c = response.indexOf(':');
			if (c != -1) {
				String m = response.substring(0, c);
				String v = response.substring(c + 1);

				if (m.equals("play")) {
					try {
						new URL(v);
					} catch (Exception e) {
						v = "ytsearch: " + v;
					}

					MantaroBot.getInstance().getAudioManager().loadAndPlay(event, v, false);
					return;
				}

				if (m.equals("embed")) {
					EmbedJSON embed;
					try {
						embed = GsonDataManager.gson(false).fromJson('{' + v + '}', EmbedJSON.class);
					} catch (Exception ignored) {
						event.getChannel().sendMessage(
							EmoteReference.ERROR2 + "The string ``{" + v + "}`` isn't a valid JSON.").queue();
						return;
					}

					event.getChannel().sendMessage(embed.gen(event)).queue();
					return;
				}

				if (m.equals("img") || m.equals("image") || m.equals("imgembed")) {
					if (!EmbedBuilder.URL_PATTERN.asPredicate().test(v)) {
						event.getChannel().sendMessage(
							EmoteReference.ERROR2 + "The string ``" + v + "`` isn't a valid link.").queue();
						return;
					}
					event.getChannel().sendMessage(new EmbedBuilder().setImage(v).setTitle(cmdName, null)
						.setColor(event.getMember().getColor()).build()).queue();
					return;
				}

				if (m.equals("iam")) {
					MiscCmds.iamFunction(v, event);
					return;
				}

				if (m.equals("iamnot")) {
					MiscCmds.iamnotFunction(v, event);
					return;
				}
			}

			event.getChannel().sendMessage(response).queue();
		}

		@Override
		public void run(GuildMessageReceivedEvent event, String cmdName, String ignored) {
			try {
				handle(cmdName, event);
			} catch (Exception e) {
				log.error("An exception occurred while processing a custom command:", e);
			}
			log("custom command");
		}
	};

	@Subscribe
	public static void custom(CommandRegistry cr) {
		String any = "[\\d\\D]*?";

		cr.register("custom", new SimpleCommand(Category.UTILS) {
			@Override
			public void call(GuildMessageReceivedEvent event, String content, String[] args) {
				if (args.length < 1) {
					onHelp(event);
					return;
				}

				String action = args[0];

				if (action.equals("list") || action.equals("ls")) {
					String filter = event.getGuild().getId() + ":";
					List<String> commands = customCommands.keySet().stream()
						.filter(s -> s.startsWith(filter))
						.map(s -> s.substring(filter.length()))
						.collect(Collectors.toList());

					EmbedBuilder builder = new EmbedBuilder()
						.setAuthor("Commands for this guild", null, event.getGuild().getIconUrl())
						.setColor(event.getMember().getColor());
					builder.setDescription(
						commands.isEmpty() ? "There is nothing here, just dust." : checkString(forType(commands)));

					event.getChannel().sendMessage(builder.build()).queue();
					return;
				}

				if (db().getGuild(event.getGuild()).getData().isCustomAdminLock() && !CommandPermission.ADMIN.test(
					event.getMember())) {
					event.getChannel().sendMessage("This guild only accepts custom commands from administrators.")
						.queue();
					return;
				}

				if (action.equals("clear")) {
					if (!CommandPermission.ADMIN.test(event.getMember())) {
						event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot do that, silly.").queue();
						return;
					}

					List<OldCustomCommand> customCommands = db().getCustomCommands(event.getGuild());

					if (customCommands.isEmpty()) {
						event.getChannel().sendMessage(
							EmoteReference.ERROR + "There's no Custom Commands registered in this Guild.").queue();
					}
					int size = customCommands.size();
					customCommands.forEach(OldCustomCommand::deleteAsync);
					customCommands.forEach(c -> CustomCmds.customCommands.remove(c.getId()));
					event.getChannel().sendMessage(EmoteReference.PENCIL + "Cleared **" + size + " Custom Commands**!")
						.queue();
					return;
				}

				if (args.length < 2) {
					onHelp(event);
					return;
				}

				String cmd = args[1];

				if (action.equals("make")) {
					if (!NAME_PATTERN.matcher(cmd).matches()) {
						event.getChannel().sendMessage(EmoteReference.ERROR + "Not allowed character.").queue();
						return;
					}

					List<String> responses = new ArrayList<>();
					boolean created = InteractiveOperations.create(
						event.getChannel(), 60, e -> {
							if (!e.getAuthor().equals(event.getAuthor())) return Operation.IGNORED;

							String c = e.getMessage().getRawContent();
							if (!c.startsWith("&")) return Operation.IGNORED;
							c = c.substring(1);

							if (c.startsWith("~>cancel") || c.startsWith("~>stop")) {
								event.getChannel().sendMessage(EmoteReference.CORRECT + "Command Creation canceled.")
									.queue();
								return Operation.COMPLETED;
							}

							if (c.startsWith("~>save")) {
								String arg = c.substring(6).trim();
								String saveTo = !arg.isEmpty() ? arg : cmd;

								if (!NAME_PATTERN.matcher(cmd).matches()) {
									event.getChannel().sendMessage(EmoteReference.ERROR + "Not allowed character.")
										.queue();
									return Operation.RESET_TIMEOUT;
								}

								if(cmd.length() >= 100){
									event.getChannel().sendMessage(EmoteReference.ERROR + "Name is too long.")
											.queue();
									return Operation.RESET_TIMEOUT;
								}

								if (CommandListener.PROCESSOR.commands().containsKey(
									saveTo) && !CommandListener.PROCESSOR.commands().get(saveTo).equals(
									customCommand)) {
									event.getChannel().sendMessage(
										EmoteReference.ERROR + "A command already exists with this name!").queue();
									return Operation.RESET_TIMEOUT;
								}

								if (responses.isEmpty()) {
									event.getChannel().sendMessage(
										EmoteReference.ERROR + "No responses were added. Stopping creation without saving...")
										.queue();
								} else {
									OldCustomCommand custom = OldCustomCommand.of(event.getGuild().getId(), cmd, responses);

									//save at DB
									custom.saveAsync();

									//reflect at local
									customCommands.put(custom.getId(), custom.getValues());

									//add mini-hack
									CommandListener.PROCESSOR.commands().put(cmd, customCommand);

									event.getChannel().sendMessage(
										EmoteReference.CORRECT + "Saved to command ``" + cmd + "``!").queue();

									//easter egg :D
									TextChannelGround.of(event).dropItemWithChance(8, 2);
								}
								return Operation.COMPLETED;
							}

							responses.add(c);
							e.getMessage().addReaction(EmoteReference.CORRECT.getUnicode()).queue();
							return Operation.RESET_TIMEOUT;
						}) != null;

					if (created) {
						event.getChannel().sendMessage(
							EmoteReference.PENCIL + "Started **\"Creation of Custom Command ``" + cmd + "``\"**!\nSend ``&~>stop`` to stop creation **without saving**.\nSend ``&~>save`` to stop creation an **save the new Command**. Send any text beginning with ``&`` to be added to the Command Responses.\nThis Interactive Operation ends without saving after 60 seconds of inactivity.")
							.queue();
					} else {
						event.getChannel().sendMessage(
							EmoteReference.ERROR + "There's already an Interactive Operation happening on this channel.")
							.queue();
					}

					return;
				}

				if (action.equals("remove") || action.equals("rm")) {
					if (!NAME_PATTERN.matcher(cmd).matches()) {
						event.getChannel().sendMessage(EmoteReference.ERROR + "Not allowed character.").queue();
						return;
					}

					OldCustomCommand custom = db().getCustomCommand(event.getGuild(), cmd);
					if (custom == null) {
						event.getChannel().sendMessage(
							EmoteReference.ERROR2 + "There's no Custom Command ``" + cmd + "`` in this Guild.").queue();
						return;
					}

					//delete at DB
					custom.deleteAsync();

					//reflect at local
					customCommands.remove(custom.getId());

					//clear commands if none
					if (customCommands.keySet().stream().noneMatch(s -> s.endsWith(":" + cmd)))
						CommandListener.PROCESSOR.commands().remove(cmd);

					event.getChannel().sendMessage(EmoteReference.PENCIL + "Removed Custom Command ``" + cmd + "``!")
						.queue();

					return;
				}

				if (action.equals("raw")) {
					if (!NAME_PATTERN.matcher(cmd).matches()) {
						event.getChannel().sendMessage(EmoteReference.ERROR + "Not allowed character.").queue();
						return;
					}

					OldCustomCommand custom = db().getCustomCommand(event.getGuild(), cmd);
					if (custom == null) {
						event.getChannel().sendMessage(
							EmoteReference.ERROR2 + "There's no Custom Command ``" + cmd + "`` in this Guild.").queue();
						return;
					}

					Pair<String, Integer> pair = DiscordUtils.embedList(custom.getValues(), Object::toString);

					event.getChannel().sendMessage(baseEmbed(event, "Command \"" + cmd + "\":")
						.setDescription(pair.getLeft())
						.setFooter(
							"(Showing " + pair.getRight() + " responses of " + custom.getValues().size() + ")", null)
						.build()
					).queue();
					return;
				}

				if (action.equals("import")) {
					if (!NAME_WILDCARD_PATTERN.matcher(cmd).matches()) {
						event.getChannel().sendMessage(EmoteReference.ERROR + "Not allowed character.").queue();
						return;
					}

					Map<String, Guild> mapped = MantaroBot.getInstance().getMutualGuilds(event.getAuthor()).stream()
						.collect(Collectors.toMap(ISnowflake::getId, g -> g));

					List<Pair<Guild, OldCustomCommand>> filtered = MantaroData.db()
						.getCustomCommandsByName(("*" + cmd + "*").replace("*", any)).stream()
						.map(customCommand -> {
							Guild guild = mapped.get(customCommand.getGuildId());
							return guild == null ? null : Pair.of(guild, customCommand);
						})
						.filter(Objects::nonNull)
						.collect(Collectors.toList());

					if (filtered.size() == 0) {
						event.getChannel().sendMessage(
							EmoteReference.ERROR + "There are no custom commands matching your search query.").queue();
						return;
					}

					DiscordUtils.selectList(
						event, filtered,
						pair -> "``" + pair.getValue().getName() + "`` - Guild: ``" + pair.getKey() + "``",
						s -> baseEmbed(event, "Select the Command:").setDescription(s)
							.setFooter(
								"(You can only select custom commands from guilds that you are a member of)",
								null
							).build(),
						pair -> {
							String cmdName = pair.getValue().getName();
							List<String> responses = pair.getValue().getValues();
							OldCustomCommand custom = OldCustomCommand.of(event.getGuild().getId(), cmdName, responses);

							//save at DB
							custom.saveAsync();

							//reflect at local
							customCommands.put(custom.getId(), custom.getValues());

							event.getChannel().sendMessage(String
								.format("Imported custom command ``%s`` from guild `%s` with responses ``%s``", cmdName,
									pair.getKey().getName(), String.join("``, ``", responses)
								)).queue();

							//easter egg :D
							TextChannelGround.of(event).dropItemWithChance(8, 2);
						}
					);

					return;
				}

				if (args.length < 3) {
					onHelp(event);
					return;
				}

				String value = args[2];

				if (action.equals("rename")) {
					if (!NAME_PATTERN.matcher(cmd).matches() || !NAME_PATTERN.matcher(value).matches()) {
						event.getChannel().sendMessage(EmoteReference.ERROR + "Not allowed character.").queue();
						return;
					}

					if (CommandListener.PROCESSOR.commands().containsKey(value) && !CommandListener.PROCESSOR.commands()
						.get(value).equals(customCommand)) {
						event.getChannel().sendMessage(
							EmoteReference.ERROR + "A command already exists with this name!").queue();
						return;
					}

					OldCustomCommand oldCustom = db().getCustomCommand(event.getGuild(), cmd);

					if (oldCustom == null) {
						event.getChannel().sendMessage(
							EmoteReference.ERROR2 + "There's no Custom Command ``" + cmd + "`` in this Guild.").queue();
						return;
					}

					OldCustomCommand newCustom = OldCustomCommand.of(event.getGuild().getId(), value, oldCustom.getValues());

					//change at DB
					oldCustom.deleteAsync();
					newCustom.saveAsync();

					//reflect at local
					customCommands.remove(oldCustom.getId());
					customCommands.put(newCustom.getId(), newCustom.getValues());

					//add mini-hack
					CommandListener.PROCESSOR.commands().put(cmd, customCommand);

					//clear commands if none
					if (customCommands.keySet().stream().noneMatch(s -> s.endsWith(":" + cmd)))
						CommandListener.PROCESSOR.commands().remove(cmd);

					event.getChannel().sendMessage(
						EmoteReference.CORRECT + "Renamed command ``" + cmd + "`` to ``" + value + "``!").queue();

					//easter egg :D
					TextChannelGround.of(event).dropItemWithChance(8, 2);
					return;
				}

				if (action.equals("add") || action.equals("new")) {
					if (!NAME_PATTERN.matcher(cmd).matches()) {
						event.getChannel().sendMessage(EmoteReference.ERROR + "Not allowed character.").queue();
						return;
					}

					if(cmd.length() >= 100){
						event.getChannel().sendMessage(EmoteReference.ERROR + "Name is too long.")
								.queue();
						return;
					}

					if (CommandListener.PROCESSOR.commands().containsKey(cmd) && !CommandListener.PROCESSOR.commands()
						.get(cmd).equals(customCommand)) {
						event.getChannel().sendMessage(
							EmoteReference.ERROR + "A command already exists with this name!").queue();
						return;
					}

					OldCustomCommand custom = OldCustomCommand.of(
						event.getGuild().getId(), cmd, Collections.singletonList(value));

					if (action.equals("add")) {
						OldCustomCommand c = db().getCustomCommand(event, cmd);

						if (c != null) custom.getValues().addAll(c.getValues());
					}

					//save at DB
					custom.saveAsync();

					//reflect at local
					customCommands.put(custom.getId(), custom.getValues());

					//add mini-hack
					CommandListener.PROCESSOR.commands().put(cmd, customCommand);

					event.getChannel().sendMessage(EmoteReference.CORRECT + "Saved to command ``" + cmd + "``!")
						.queue();

					//easter egg :D
					TextChannelGround.of(event).dropItemWithChance(8, 2);
					return;
				}

				onHelp(event);
			}

			@Override
			public String[] splitArgs(String content) {
				return SPLIT_PATTERN.split(content, 3);
			}

			@Override
			public MessageEmbed help(GuildMessageReceivedEvent event) {
				return helpEmbed(event, "CustomCommand Manager")
					.setDescription("**Manages the Custom Commands of the Guild.**")
					.addField("Guide", "https://github.com/Mantaro/MantaroBot/wiki/Custom-Commands", false)
					.addField(
						"Usage:",
						"`~>custom` - Shows this help\n" +
							"`~>custom <list|ls> [detailed]` - **List all commands. If detailed is supplied, it prints the responses of each command.**\n" +
							"`~>custom clear` - **Remove all Custom Commands from this Guild. (ADMIN-ONLY)**\n" +
							"`~>custom new <name> <response>` - **Creates a new custom command with one response. Use `custom add` to add more.**\n" +
							"`~>custom add <name> <response>` - **Adds the response provided to a custom command.**\n" +
							"`~>custom make <name>` - **Starts a Interactive Operation to create a command with the specified name.**\n" +
							"`~>custom <remove|rm> <name>` - **Removes a command with an specific name.**\n" +
							"`~>custom import <search>` - **Imports a command from another guild you're in.**",
						false
					).build();
			}
		});
	}

	@Subscribe
	public static void onPostLoad(PostLoadEvent e) {
		db().getCustomCommands().forEach(custom -> {
			if (!NAME_PATTERN.matcher(custom.getName()).matches()) {
				String newName = INVALID_CHARACTERS_PATTERN.matcher(custom.getName()).replaceAll("_");
				log.info("Custom Command with Invalid Characters '%s' found. Replacing with '%'", custom.getName());

				custom.deleteAsync();
				custom = OldCustomCommand.of(custom.getGuildId(), newName, custom.getValues());
				custom.saveAsync();
			}

			if (CommandListener.PROCESSOR.commands().containsKey(custom.getName()) && !CommandListener.PROCESSOR
				.commands().get(custom.getName()).equals(customCommand)) {
				custom.deleteAsync();
				custom = OldCustomCommand.of(custom.getGuildId(), "_" + custom.getName(), custom.getValues());
				custom.saveAsync();
			}

			//add mini-hack
			CommandListener.PROCESSOR.commands().put(custom.getName(), customCommand);

			customCommands.put(custom.getId(), custom.getValues());
		});
	}
}
