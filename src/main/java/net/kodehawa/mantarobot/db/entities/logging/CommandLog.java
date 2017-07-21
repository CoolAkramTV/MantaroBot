package net.kodehawa.mantarobot.db.entities.logging;

import lombok.Data;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.kodehawa.mantarobot.db.ManagedDatabase;
import net.kodehawa.mantarobot.db.ManagedObject;

import java.time.OffsetDateTime;

import static com.rethinkdb.RethinkDB.r;
import static net.kodehawa.mantarobot.data.MantaroData.conn;

@Data
public class CommandLog implements ManagedObject {
	public static final String DB_TABLE = "cmdlog";

	public static void log(String cmd, String args, GuildMessageReceivedEvent event, boolean successful) {
		CommandLog log = new CommandLog();

		log.setId(String.valueOf(ManagedDatabase.LOG_WORKER.generate()));

		log.setGuildId(event.getGuild().getId());
		log.setChannelId(event.getChannel().getId());
		log.setUserId(event.getAuthor().getId());

		log.setCmd(cmd);
		log.setArgs(args);
		log.setSuccessful(successful);

		log.save();
	}

	private String cmd, args;
	private OffsetDateTime date;
	private String id, userId, channelId, guildId;
	private boolean successful;

	@Override
	public void delete() {
		r.table(DB_TABLE).get(getId()).delete().run(conn());
	}

	@Override
	public void save() {
		r.table(DB_TABLE).insert(this)
			.optArg("conflict", "replace")
			.run(conn());
	}
}
