package carpet.commands;

import com.mojang.brigadier.CommandDispatcher;

import carpet.CarpetServer;
import carpet.script.LineLocker.*;
import carpet.utils.Messenger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.*;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.minecraft.commands.Commands.*;

public class BreakpointCommand {
	public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				literal("breakpoint")
					.requires(source -> source.hasPermission(2))
					.then(argument("app", word())
							.suggests((context, builder) -> SharedSuggestionProvider.suggest(CarpetServer.scriptServer.modules.keySet(), builder))
							.executes(ctx -> showParkedThreads(ctx.getSource(), getString(ctx, "app")))
							.then(literal("list")
									.executes(ctx -> showBreakpointsFor(ctx.getSource(), getString(ctx, "app"))))
							.then(literal("add")
									.then(argument("line", integer(0))
											.executes(ctx -> addBreakpointTo(ctx.getSource(), getString(ctx, "app"), getInteger(ctx, "line")))))
							.then(literal("remove")
									.then(argument("line", integer(0))
											.executes(ctx -> removeBreakpointIn(ctx.getSource(), getString(ctx, "app"), getInteger(ctx, "line")))))
							.then(literal("step")
									.then(argument("line", integer(0))
											.executes(ctx -> stepBreakpointsAt(ctx.getSource(), getString(ctx, "app"), getInteger(ctx, "line")))))
							)
				);
	}

	private static final Map<String, ArrayList<BreakpointActivation>> PICKED_UP_ACTIVATIONS = new HashMap<>();
	private static final Map<String, ArrayList<Breakpoint>> ENABLED_BREAKPOINTS = new HashMap<>();
	
	private static int showParkedThreads(CommandSourceStack source, String app) {
		updateActivationsFor(app);
		int count = 0;
		for (BreakpointActivation activation : get(PICKED_UP_ACTIVATIONS, app)) {
			printActivation(source, activation, app);
			count++;
		}
		if (count == 0) source.sendSuccess(Component.literal("No breakpoint activations"), false);
		return 1;
	}
	
	private static int showBreakpointsFor(CommandSourceStack source, String app) {
		int count = 0;
		for (Breakpoint b : get(ENABLED_BREAKPOINTS, app)) {
			Messenger.m(source, "w Breakpoint in line ", "d " + b.line() + " ", 
					"y [DISABLE]", "!/breakpoint " + app + " remove " + b.line(), "^g Click to disable this breakpoint");
			get(PICKED_UP_ACTIVATIONS, app).stream()
				.filter(act -> b == act.breakpoint())
				.forEach(act -> printActivation(source, act, app));
			count++;
		}
		if (count == 0) source.sendSuccess(Component.literal("No active breakpoints"), false);
		return 1;
	}

	private static int stepBreakpointsAt(CommandSourceStack source, String app, int lineNumber) {
		updateActivationsFor(app);
		int count = 0;
		Iterator<BreakpointActivation> iterator = get(PICKED_UP_ACTIVATIONS, app).iterator();
		while (iterator.hasNext()) {
			var activation = iterator.next();
			if (activation.breakpoint().line() == lineNumber) {
				count++;
				activation.step();
				iterator.remove();
			}
		}
		source.sendSuccess(Component.literal("Stepped " + count + " activations"), false);
		return 1;
	}
	
	private static int addBreakpointTo(CommandSourceStack source, String app, int lineNumber) {
		get(ENABLED_BREAKPOINTS, app).add(Objects.requireNonNull(CarpetServer.scriptServer.modules.get(app).locker.addBreakpoint(lineNumber)));
		source.sendSuccess(Component.literal("Added breakpoint"), false);
		return 1;
	}

	private static int removeBreakpointIn(CommandSourceStack source, String app, int lineNumber) {
		Breakpoint breakpoint = get(ENABLED_BREAKPOINTS, app).stream().filter(br -> lineNumber == br.line()).findAny().get();
		get(ENABLED_BREAKPOINTS, app).remove(breakpoint);
		breakpoint.disable();
		source.sendSuccess(Component.literal("Removed breakpoint"), false);
		return 1;
	}

	private static void printActivation(CommandSourceStack source, BreakpointActivation activation, String app) {
		Messenger.m(source, "l - Activation in ", "d " + app, "l  in line ", "d " + activation.breakpoint().line(), "l  on ", "d " + activation.ownerThread() + " ",
				"y [STEP]", "!/breakpoint " + app + " step " + activation.breakpoint().line(), "^g Click to step this activation");
	}
	
	private static void updateActivationsFor(String app) {
		List<BreakpointActivation> activations = get(PICKED_UP_ACTIVATIONS, app);
		for (Breakpoint b : get(ENABLED_BREAKPOINTS, app)) {
			BreakpointActivation activation;
			while ((activation = b.poll()) != null)
				activations.add(activation);
		}
	}
	
	private static <T, K> ArrayList<T> get(Map<K, ArrayList<T>> map, K key) {
		return map.computeIfAbsent(key, s -> new ArrayList<>());
	}
}
