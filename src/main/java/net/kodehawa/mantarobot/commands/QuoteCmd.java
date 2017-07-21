package net.kodehawa.mantarobot.commands;

import br.com.brjdevs.java.utils.collections.CollectionUtils;
import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.db.ManagedDatabase;
import net.kodehawa.mantarobot.db.entities.QuotedMessage;
import net.kodehawa.mantarobot.modules.CommandRegistry;
import net.kodehawa.mantarobot.modules.Module;
import net.kodehawa.mantarobot.modules.commands.SimpleCommand;
import net.kodehawa.mantarobot.modules.commands.base.Category;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Module
public class QuoteCmd {
	@Subscribe
	public static void quote(CommandRegistry cr) {
		cr.register("quote", new SimpleCommand(Category.MISC) {
			@Override
			protected void call(GuildMessageReceivedEvent event, String content, String[] args) {
				if (content.isEmpty()) {
					onHelp(event);
					return;
				}

				String action = args[0];
				String phrase = content.replace(action + " ", "");
				Guild guild = event.getGuild();
				ManagedDatabase db = MantaroData.db();
				EmbedBuilder builder = new EmbedBuilder();
				SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
				List<Message> messageHistory;
				try {
					messageHistory = event.getChannel().getHistory().retrievePast(100).complete();
				} catch (Exception e) {
					if(e instanceof PermissionException){
						event.getChannel().sendMessage(EmoteReference.CRYING + "I don't have permission to do this :<").queue();
						return;
					}

					event.getChannel().sendMessage(EmoteReference.ERROR + "It seems like discord is on fire, as my" +
						" " +
						"request to retrieve message history was denied" +
						"with the error `" + e.getClass().getSimpleName() + "`").queue();
					return;
				}

				if (action.equals("addfrom")) {
					Message message = messageHistory.stream().filter(
						msg -> msg.getContent().toLowerCase().contains(phrase.toLowerCase())
							&& !msg.getContent().startsWith(
							db.getGuild(guild).getData().getGuildCustomPrefix() == null ? "~>"
								: db.getGuild(guild).getData().getGuildCustomPrefix())
							&& Stream.of(MantaroData.config().get().getPrefix()).noneMatch(p -> msg.getContent().startsWith(p))
					).findFirst().orElse(null);

					if (message == null) {
						event.getChannel().sendMessage(EmoteReference.ERROR + "I couldn't find a message matching the specified search" +
							" criteria. Please try again with a more specific query.").queue();
						return;
					}

					QuotedMessage quote = new QuotedMessage(message);
					quote.saveAsync();
					event.getChannel().sendMessage(buildQuoteEmbed(dateFormat, builder, quote)).queue();
					quote.save();
					return;
				}

				if (action.equals("random")) {
					try {
						QuotedMessage quote = CollectionUtils.random(db.getQuotes(event.getGuild()));
						event.getChannel().sendMessage(buildQuoteEmbed(dateFormat, builder, quote)).queue();
					} catch (Exception e) {
						event.getChannel().sendMessage(EmoteReference.ERROR + "This server has no set quotes!").queue();
					}
					return;
				}

				if (action.equals("readfrom")) {
					try {
						List<QuotedMessage> quotes = db.getQuotes(guild);
						for (int i2 = 0; i2 < quotes.size(); i2++) {
							if (quotes.get(i2).getContent().contains(phrase)) {
								QuotedMessage quote = quotes.get(i2);
								event.getChannel().sendMessage(buildQuoteEmbed(dateFormat, builder, quote)).queue();
								break;
							}
						}
					} catch (Exception e) {
						event.getChannel().sendMessage(EmoteReference.ERROR + "I didn't find any quotes! (no quotes match the criteria).").queue();
					}
					return;
				}

				if (action.equals("removefrom")) {
					try {
						List<QuotedMessage> quotes = db.getQuotes(guild);
						for (int i2 = 0; i2 < quotes.size(); i2++) {
							if (quotes.get(i2).getContent().contains(phrase)) {
								QuotedMessage quote = quotes.get(i2);
								db.getQuotes(guild).remove(i2);
								quote.saveAsync();
								event.getChannel().sendMessage(EmoteReference.CORRECT + "Removed quote with content: " + quote.getContent())
									.queue();
								break;
							}
						}
					} catch (Exception e) {
						event.getChannel().sendMessage(EmoteReference.ERROR + "No quotes match the criteria.").queue();
					}
				}
			}

			@Override
			public MessageEmbed help(GuildMessageReceivedEvent event) {
				return helpEmbed(event, "Quote command")
					.setDescription("**Quotes a message by search term.**")
					.addField("Usage",
						"`~>quote addfrom <phrase>`- **Add a quote with the content defined by the specified number. For example, providing 1 will quote " +
						"the last message.**\n"
						+ "`~>quote removefrom <phrase>` - **Remove a quote based on your text query.**\n"
						+ "`~>quote readfrom <phrase>` - **Search for the first quote which matches your search criteria and prints " +
						"it.**\n"
						+ "`~>quote random` - **Get a random quote.** \n", false)
					.addField("Parameters", "`phrase` - A part of the quote phrase.", false)
					.setColor(Color.DARK_GRAY)
					.build();
			}
		});
	}

	private static MessageEmbed buildQuoteEmbed(SimpleDateFormat dateFormat, EmbedBuilder builder, QuotedMessage quote) {
		builder.setAuthor(quote.getUserName() + " said: ", null, quote.getUserAvatar())
			.setDescription("Quote made in server " + quote.getGuildName() + " in channel #" + quote.getChannelName())
			.addField("Content", quote.getContent(), false)
			.setThumbnail(quote.getUserAvatar())
			.setFooter("Date: " + dateFormat.format(new Date(System.currentTimeMillis())), null);
		return builder.build();
	}
}