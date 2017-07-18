package net.kodehawa.mantarobot.commands;

import com.google.common.eventbus.Subscribe;
import com.rethinkdb.gen.ast.OrderBy;
import com.rethinkdb.model.OptArgs;
import com.rethinkdb.net.Cursor;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.kodehawa.dataporter.oldentities.OldPlayer;
import net.kodehawa.mantarobot.MantaroBot;
import net.kodehawa.mantarobot.commands.currency.RateLimiter;
import net.kodehawa.mantarobot.commands.currency.TextChannelGround;
import net.kodehawa.mantarobot.commands.currency.item.ItemStack;
import net.kodehawa.mantarobot.commands.currency.item.Items;
import net.kodehawa.mantarobot.core.listeners.operations.old.InteractiveOperationListener;
import net.kodehawa.mantarobot.core.listeners.operations.old.InteractiveOperations;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.modules.CommandRegistry;
import net.kodehawa.mantarobot.modules.Module;
import net.kodehawa.mantarobot.modules.commands.SimpleCommand;
import net.kodehawa.mantarobot.modules.commands.base.Category;
import net.kodehawa.mantarobot.utils.Utils;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;
import org.apache.commons.lang3.tuple.Pair;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.rethinkdb.RethinkDB.r;

/**
 * Basically part of CurrencyCmds, but only the money commands.
 */
@Module
public class MoneyCmds {

    private static Random random = new Random();

    @Subscribe
    public static void daily(CommandRegistry cr) {
        RateLimiter rateLimiter = new RateLimiter(TimeUnit.HOURS, 24);
        Random r = new Random();
        cr.register("daily", new SimpleCommand(Category.CURRENCY) {
            @Override
            public void call(GuildMessageReceivedEvent event, String content, String[] args) {
                String id = event.getAuthor().getId();
                long money = 150L;
                User mentionedUser = null;
                try {
                    mentionedUser = event.getMessage().getMentionedUsers().get(0);
                } catch(IndexOutOfBoundsException ignored) {
                }

                OldPlayer player = mentionedUser != null ? MantaroData.db().getPlayer(event.getGuild().getMember(mentionedUser)) : MantaroData.db().getPlayer(event.getMember());

                if(player.isLocked()) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + (mentionedUser != null ? "That user cannot receive daily credits now." : "You cannot get daily credits now.")).queue();
                    return;
                }

                if(!rateLimiter.process(id)) {
                    event.getChannel().sendMessage(EmoteReference.STOPWATCH +
                            "Halt! You can only do this once every 24 hours.\n**You'll be able to use this command again in " +
                            Utils.getVerboseTime(rateLimiter.tryAgainIn(id))
                            + ".**").queue();
                    return;
                }

                if(mentionedUser != null && !mentionedUser.getId().equals(event.getAuthor().getId())) {
                    money = money + r.nextInt(2);

                    if(player.getInventory().containsItem(Items.COMPANION)) money = Math.round(money + (money * 0.10));

                    if(mentionedUser.getId().equals(player.getData().getMarriedWith()) && player.getData().getMarriedSince() != null &&
                            Long.parseLong(player.getData().anniversary()) - player.getData().getMarriedSince() > TimeUnit.DAYS.toMillis(1)) {
                        money = money + r.nextInt(20);

                        if(player.getInventory().containsItem(Items.RING_2)) {
                            money = money + r.nextInt(10);
                        }
                    }

                    player.addMoney(money);
                    player.save();

                    event.getChannel().sendMessage(EmoteReference.CORRECT + "I gave your **$" + money + "** daily credits to " +
                            mentionedUser.getName()).queue();
                    return;
                }

                player.addMoney(money);
                player.save();
                event.getChannel().sendMessage(EmoteReference.CORRECT + "You got **$" + money + "** daily credits.").queue();
            }

            @Override
            public MessageEmbed help(GuildMessageReceivedEvent event) {
                return helpEmbed(event, "Daily command")
                        .setDescription("**Gives you $150 credits per day (or between 150 and 180 if you transfer it to another person)**.")
                        .build();
            }
        });
    }

    @Subscribe
    public static void gamble(CommandRegistry cr) {
        RateLimiter rateLimiter = new RateLimiter(TimeUnit.SECONDS, 15);
        SecureRandom r = new SecureRandom();

        cr.register("gamble", new SimpleCommand(Category.CURRENCY) {
            @Override
            public void call(GuildMessageReceivedEvent event, String content, String[] args) {
                String id = event.getAuthor().getId();
                OldPlayer player = MantaroData.db().getPlayer(event.getMember());

                if(!rateLimiter.process(id)) {
                    event.getChannel().sendMessage(EmoteReference.STOPWATCH +
                            "Halt! You're gambling so fast that I can't print enough money!").queue();
                    return;
                }

                if(player.getMoney() <= 0) {
                    event.getChannel().sendMessage(EmoteReference.ERROR2 + "You're broke. Search for some credits first!").queue();
                    return;
                }

                if(player.getMoney() > (long) (Integer.MAX_VALUE) * 3) {
                    event.getChannel().sendMessage(EmoteReference.ERROR2 + "You have too much money! Maybe transfer or buy items?").queue();
                    return;
                }

                double multiplier;
                long i;
                int luck;
                try {
                    switch(content) {
                        case "all":
                        case "everything":
                            i = player.getMoney();
                            multiplier = 1.4d + (r.nextInt(1500) / 1000d);
                            luck = 30 + (int) (multiplier * 10) + r.nextInt(20);
                            break;
                        case "half":
                            i = player.getMoney() == 1 ? 1 : player.getMoney() / 2;
                            multiplier = 1.2d + (r.nextInt(1500) / 1000d);
                            luck = 20 + (int) (multiplier * 15) + r.nextInt(20);
                            break;
                        case "quarter":
                            i = player.getMoney() == 1 ? 1 : player.getMoney() / 4;
                            multiplier = 1.1d + (r.nextInt(1100) / 1000d);
                            luck = 25 + (int) (multiplier * 10) + r.nextInt(18);
                            break;
                        default:
                            i = Long.parseLong(content);
                            if(i > player.getMoney() || i < 0) throw new UnsupportedOperationException();
                            multiplier = 1.1d + (i / player.getMoney() * r.nextInt(1300) / 1000d);
                            luck = 15 + (int) (multiplier * 15) + r.nextInt(10);
                            break;
                    }
                } catch(NumberFormatException e) {
                    event.getChannel().sendMessage(EmoteReference.ERROR2 + "Please type a valid number equal or less than your credits or" +
                            " `all` to gamble all your credits.").queue();
                    return;
                } catch(UnsupportedOperationException e) {
                    event.getChannel().sendMessage(EmoteReference.ERROR2 + "Please type a value within your credits amount.").queue();
                    return;
                }

                User user = event.getAuthor();
                long gains = (long) (i * multiplier);
                gains = Math.round(gains * 0.55);

                final int finalLuck = luck;
                final long finalGains = gains;

                player.setLocked(true);

                if(i >= Integer.MAX_VALUE / 4) {
                    event.getChannel().sendMessage(EmoteReference.WARNING + "You're about to bet **" + i + "** " +
                            "credits (which seems to be a lot). Are you sure? Type **yes** to continue and **no** otherwise.").queue();
                    InteractiveOperations.create(event.getChannel(), 30, new InteractiveOperationListener() {
                        @Override
                        public int run(GuildMessageReceivedEvent e) {
                            if(e.getAuthor().getId().equals(user.getId())) {
                                if(e.getMessage().getContent().equalsIgnoreCase("yes")) {
                                    proceedGamble(event, player, finalLuck, random, i, finalGains);
                                    player.setLocked(false);
                                    return COMPLETED;
                                } else if(e.getMessage().getContent().equalsIgnoreCase("no")) {
                                    e.getChannel().sendMessage(EmoteReference.ZAP + "Cancelled bet.").queue();
                                    player.setLocked(false);
                                    return COMPLETED;
                                }
                            }

                            return IGNORED;
                        }

                        @Override
                        public void onExpire() {
                            event.getChannel().sendMessage(EmoteReference.ERROR + "Time to complete the operation has ran out.")
                                    .queue();
                            player.setLocked(false);
                        }
                    });

                    return;
                }

                proceedGamble(event, player, luck, random, i, gains);
                player.setLocked(false);
            }

            @Override
            public MessageEmbed help(GuildMessageReceivedEvent event) {
                return helpEmbed(event, "Gamble command")
                        .setDescription("Gambles your money")
                        .addField("Usage", "~>gamble <all/half/quarter> or ~>gamble <amount>", false)
                        .build();
            }
        });
    }

    @Subscribe
    public static void loot(CommandRegistry cr) {
        RateLimiter rateLimiter = new RateLimiter(TimeUnit.MINUTES, 5);
        Random r = new Random();

        cr.register("loot", new SimpleCommand(Category.CURRENCY) {
            @Override
            public void call(GuildMessageReceivedEvent event, String content, String[] args) {
                String id = event.getAuthor().getId();
                OldPlayer player = MantaroData.db().getPlayer(event.getMember());

                if(player.isLocked()) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot loot now.").queue();
                    return;
                }

                if(!rateLimiter.process(id)) {
                    event.getChannel().sendMessage(EmoteReference.STOPWATCH +
                            "Cooldown a lil bit, you can only do this once every 5 minutes.\n **You'll be able to use this command again " +
                            "in " +
                            Utils.getVerboseTime(rateLimiter.tryAgainIn(event.getAuthor()))
                            + ".**").queue();
                    return;
                }

                TextChannelGround ground = TextChannelGround.of(event);

                if(r.nextInt(150) == 0) { //1 in 450 chance of it dropping a loot crate.
                    ground.dropItem(Items.LOOT_CRATE);
                }

                List<ItemStack> loot = ground.collectItems();
                int moneyFound = ground.collectMoney() + Math.max(0, r.nextInt(50) - 10);


                if(MantaroData.db().getUser(event.getMember()).isPremium() && moneyFound > 0) {
                    moneyFound = moneyFound + random.nextInt(moneyFound);
                }

                if(!loot.isEmpty()) {
                    String s = ItemStack.toString(ItemStack.reduce(loot));
                    String overflow;
                    if(player.getInventory().merge(loot)) {
                        overflow = "But you already had too many items, so you decided to throw away the excess. ";
                    } else {
                        overflow = "";
                    }
                    if(moneyFound != 0) {
                        if(player.addMoney(moneyFound)) {
                            event.getChannel().sendMessage(EmoteReference.POPPER + "Digging through messages, you found " + s + ", along " +
                                    "with $" + moneyFound + " credits!" + overflow).queue();
                        } else {
                            event.getChannel().sendMessage(EmoteReference.POPPER + "Digging through messages, you found " + s + ", along " +
                                    "with $" + moneyFound + " credits. " + overflow + "But you already had too many credits. Your bag overflowed" +
                                    ".\nCongratulations, you exploded a Java long. Here's a buggy money bag for you.").queue();
                        }
                    } else {
                        event.getChannel().sendMessage(EmoteReference.MEGA + "Digging through messages, you found " + s + ". " + overflow).queue();
                    }
                } else {
                    if(moneyFound != 0) {
                        if(player.addMoney(moneyFound)) {
                            event.getChannel().sendMessage(EmoteReference.POPPER + "Digging through messages, you found **$" + moneyFound +
                                    " credits!**").queue();
                        } else {
                            event.getChannel().sendMessage(EmoteReference.POPPER + "Digging through messages, you found $" + moneyFound +
                                    " credits. But you already had too many credits. Your bag overflowed.\nCongratulations, you exploded " +
                                    "a Java long. Here's a buggy money bag for you.").queue();
                        }
                    } else {
                        event.getChannel().sendMessage(EmoteReference.SAD + "Digging through messages, you found nothing but dust").queue();
                    }
                }
                player.save();
            }

            @Override
            public MessageEmbed help(GuildMessageReceivedEvent event) {
                return helpEmbed(event, "Loot command")
                        .setDescription("**Loot the current chat for items, for usage in Mantaro's currency system.**\n"
                                + "Currently, there are ``" + Items.ALL.length + "`` items available in chance," +
                                "in which you have a `random chance` of getting one or more.")
                        .addField("Usage", "~>loot", false)
                        .build();
            }
        });
    }

    @Subscribe
    public static void richest(CommandRegistry cr) {
        cr.register("leaderboard", new SimpleCommand(Category.CURRENCY) {
            RateLimiter rateLimiter = new RateLimiter(TimeUnit.SECONDS, 10);

            @Override
            public void call(GuildMessageReceivedEvent event, String content, String[] args) {

                if(!rateLimiter.process(event.getMember())) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "Dang! Don't you think you're going a bit too fast?").queue();
                    return;
                }


                String pattern = ":g$";
                OrderBy template =
                        r.table("players")
                                .orderBy()
                                .optArg("index", r.desc("money"));

                if(args.length > 0 && (args[0].equalsIgnoreCase("lvl") || args[0].equalsIgnoreCase("level"))) {
                    Cursor<Map> m = r.table("players")
                            .orderBy()
                            .optArg("index", r.desc("level"))
                            .filter(player -> player.g("id").match(pattern))
                            .map(player -> player.pluck("id", "level"))
                            .limit(15)
                            .run(MantaroData.conn(), OptArgs.of("read_mode", "outdated"));
                    AtomicInteger i = new AtomicInteger();
                    List<Map> c = m.toList();

                    event.getChannel().sendMessage(
                            baseEmbed(event,
                                    "Level leaderboard",
                                    event.getJDA().getSelfUser().getEffectiveAvatarUrl()
                            ).setDescription(c.stream()
                                    .map(map -> Pair.of(MantaroBot.getInstance().getUserById(map.get("id").toString().split(":")[0]), map.get("level").toString()))
                                    .filter(p -> Objects.nonNull(p.getKey()))
                                    .map(p -> String.format("%d - **%s#%s** - Level: %s", i.incrementAndGet(), p.getKey().getName(), p
                                            .getKey().getDiscriminator(), p.getValue()))
                                    .collect(Collectors.joining("\n"))
                            ).build()
                    ).queue();


                    return;
                }


                if(args.length > 0 && (args[0].equalsIgnoreCase("rep") || args[0].equalsIgnoreCase("reputation"))) {
                    Cursor<Map> m = r.table("players")
                            .orderBy()
                            .optArg("index", r.desc("reputation"))
                            .filter(player -> player.g("id").match(pattern))
                            .map(player -> player.pluck("id", "reputation"))
                            .limit(15)
                            .run(MantaroData.conn(), OptArgs.of("read_mode", "outdated"));
                    AtomicInteger i = new AtomicInteger();
                    List<Map> c = m.toList();

                    event.getChannel().sendMessage(
                            baseEmbed(event,
                                    "Reputation leaderboard",
                                    event.getJDA().getSelfUser().getEffectiveAvatarUrl()
                            ).setDescription(c.stream()
                                    .map(map -> Pair.of(MantaroBot.getInstance().getUserById(map.get("id").toString().split(":")[0]), map.get("reputation").toString()))
                                    .filter(p -> Objects.nonNull(p.getKey()))
                                    .map(p -> String.format("%d - **%s#%s** - Reputation: %s", i.incrementAndGet(), p.getKey().getName(), p
                                            .getKey().getDiscriminator(), p.getValue()))
                                    .collect(Collectors.joining("\n"))
                            ).build()
                    ).queue();


                    return;
                }

                Cursor<Map> c1 = getGlobalRichest(template, pattern);
                AtomicInteger i = new AtomicInteger();
                List<Map> c = c1.toList();

                event.getChannel().sendMessage(
                        baseEmbed(event,
                                "Money leaderboard",
                                event.getJDA().getSelfUser().getEffectiveAvatarUrl()
                        ).setDescription(c.stream()
                                .map(map -> Pair.of(MantaroBot.getInstance().getUserById(map.get("id").toString().split(":")[0]), map.get("money").toString()))
                                .filter(p -> Objects.nonNull(p.getKey()))
                                .map(p -> String.format("%d - **%s#%s** - Credits: $%s", i.incrementAndGet(), p.getKey().getName(), p
                                        .getKey().getDiscriminator(), p.getValue()))
                                .collect(Collectors.joining("\n"))
                        ).build()
                ).queue();
            }

            @Override
            public MessageEmbed help(GuildMessageReceivedEvent event) {
                return helpEmbed(event, "Leaderboard")
                        .setDescription("**Returns the leaderboard.**")
                        .addField("Usage", "`~>leaderboard` - **Returns the money leaderboard.**\n" +
                                "`~>leaderboard rep` - **Returns the reputation leaderboard.**\n" +
                                "`~>leaderboard lvl` - **Returns the level leaderboard.**", false)
                        .build();
            }
        });

        cr.registerAlias("leaderboard", "richest");
    }

    private static void proceedGamble(GuildMessageReceivedEvent event, OldPlayer player, int luck, Random r, long i, long gains) {
        if(luck > r.nextInt(110)) {
            if(player.addMoney(gains)) {
                event.getChannel().sendMessage(EmoteReference.DICE + "Congrats, you won " + gains + " credits and got to keep what you " +
                        "had!").queue();
            } else {
                event.getChannel().sendMessage(EmoteReference.DICE + "Congrats, you won " + gains + " credits. But you already had too " +
                        "many credits. Your bag overflowed.\nCongratulations, you exploded a Java long. Here's a buggy money bag for you" +
                        ".").queue();
            }
        } else {
            player.setMoney(Math.max(0, player.getMoney() - i));
            event.getChannel().sendMessage("\uD83C\uDFB2 Sadly, you lost " + (player.getMoney() == 0 ? "all your" : i) + " credits! " +
                    "\uD83D\uDE26").queue();
        }

        player.saveAsync();
    }

    private static Cursor<Map> getGlobalRichest(OrderBy template, String pattern) {
        return template.filter(player -> player.g("id").match(pattern))
                .map(player -> player.pluck("id", "money"))
                .limit(15)
                .run(MantaroData.conn(), OptArgs.of("read_mode", "outdated"));
    }
}
