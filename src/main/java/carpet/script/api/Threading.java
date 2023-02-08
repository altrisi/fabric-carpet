package carpet.script.api;

import carpet.script.CarpetContext;
import carpet.script.Expression;
import carpet.script.exception.ExpressionException;
import carpet.script.exception.InternalExpressionException;
import carpet.script.value.ThreadValue;
import carpet.script.value.Value;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletionException;

public class Threading
{
    public static void apply(final Expression expression)
    {
        //"overridden" native call to cancel if on main thread
        expression.addContextFunction("task_join", 1, (c, t, lv) -> {
            if (((CarpetContext) c).server().isSameThread())
            {
                throw new InternalExpressionException("'task_join' cannot be called from main thread to avoid deadlocks");
            }
            final Value v = lv.get(0);
            if (!(v instanceof final ThreadValue tv))
            {
                throw new InternalExpressionException("'task_join' could only be used with a task value");
            }
            return tv.join();
        });

        // has to be lazy due to deferred execution of the expression
        expression.addLazyFunctionWithDelegation("task_dock", 1, false, true, (c, t, expr, tok, lv) -> {
            final CarpetContext cc = (CarpetContext) c;
            final MinecraftServer server = cc.server();
            if (server.isSameThread())
            {
                return lv.get(0); // pass through for on thread tasks
            }
            final Value[] result = new Value[]{Value.NULL};
            final RuntimeException[] internal = new RuntimeException[]{null};
            try
            {
                ((CarpetContext) c).server().executeBlocking(() ->
                {
                    try
                    {
                        result[0] = lv.get(0).evalValue(c, t);
                    }
                    catch (final ExpressionException exc)
                    {
                        internal[0] = exc;
                    }
                    catch (final InternalExpressionException exc)
                    {
                        internal[0] = new ExpressionException(c, expr, tok, exc.getMessage(), exc.stack);
                    }

                    catch (final ArithmeticException exc)
                    {
                        internal[0] = new ExpressionException(c, expr, tok, "Your math is wrong, " + exc.getMessage());
                    }
                });
            }
            catch (final CompletionException exc)
            {
                throw new InternalExpressionException("Error while executing docked task section, internal stack trace is gone");
            }
            if (internal[0] != null)
            {
                throw internal[0];
            }
            final Value ret = result[0]; // preventing from lazy evaluating of the result in case a future completes later
            return (ct, tt) -> ret;
            // pass through placeholder
            // implmenetation should dock the task on the main thread.
        });
    }
}
