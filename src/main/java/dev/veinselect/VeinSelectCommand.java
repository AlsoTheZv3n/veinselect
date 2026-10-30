package dev.veinselect;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * {@code /veinselect} – eigene Limits anzeigen<br>
 * {@code /veinselect max <anzahl>} – eigenes Limit setzen (wird im Client gespeichert)<br>
 * {@code /veinselect serverlimit [anzahl]} – Server-Limit anzeigen / als Owner setzen
 */
final class VeinSelectCommand {
	private VeinSelectCommand() {}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("veinselect")
				.executes(VeinSelectCommand::info)
				.then(Commands.literal("max")
						.then(Commands.argument("anzahl", IntegerArgumentType.integer(1, VeinSelect.HARD_LIMIT))
								.executes(VeinSelectCommand::setMax)))
				.then(Commands.literal("serverlimit")
						.executes(VeinSelectCommand::getServerLimit)
						.then(Commands.argument("anzahl", IntegerArgumentType.integer(1, VeinSelect.HARD_LIMIT))
								.requires(VeinSelectCommand::isOwner)
								.executes(VeinSelectCommand::setServerLimit))));
	}

	private static boolean isOwner(CommandSourceStack source) {
		// Konsole hat alle Rechte; Spieler über OP-Level 4 oder als Singleplayer-/LAN-Host
		ServerPlayer player = source.getPlayer();
		return source.permissions().hasPermission(Permissions.COMMANDS_OWNER) || (player != null && VeinSelect.isOwner(player));
	}

	private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		int effective = VeinSelect.effectiveLimit(player);

		context.getSource().sendSuccess(() -> Component.translatableWithFallback("command.veinselect.info",
				"Vein Select: effective limit %s (your setting %s, allowed %s, server limit %s)",
				effective, VeinSelect.preferredLimit(player), VeinSelect.allowedLimit(player), VeinSelectConfig.serverLimit()), false);
		return effective;
	}

	private static int setMax(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		VeinSelect.setPreferredLimit(player, IntegerArgumentType.getInteger(context, "anzahl"));
		int effective = VeinSelect.effectiveLimit(player);

		context.getSource().sendSuccess(() -> Component.translatableWithFallback("command.veinselect.max",
				"Your limit is now %s (effective: %s)", VeinSelect.preferredLimit(player), effective), false);
		return effective;
	}

	private static int getServerLimit(CommandContext<CommandSourceStack> context) {
		int limit = VeinSelectConfig.serverLimit();
		context.getSource().sendSuccess(() -> Component.translatableWithFallback("command.veinselect.serverlimit.get",
				"Server limit: %s blocks", limit), false);
		return limit;
	}

	private static int setServerLimit(CommandContext<CommandSourceStack> context) {
		VeinSelectConfig.setServerLimit(IntegerArgumentType.getInteger(context, "anzahl"));
		int limit = VeinSelectConfig.serverLimit();
		VeinSelect.syncLimits(context.getSource().getServer());

		context.getSource().sendSuccess(() -> Component.translatableWithFallback("command.veinselect.serverlimit.set",
				"Server limit set to %s blocks", limit), true);
		return limit;
	}
}
