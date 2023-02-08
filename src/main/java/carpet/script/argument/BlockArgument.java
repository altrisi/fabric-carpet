package carpet.script.argument;

import carpet.script.CarpetContext;
import carpet.script.exception.InternalExpressionException;
import carpet.script.value.BlockValue;
import carpet.script.value.ListValue;
import carpet.script.value.NumericValue;
import carpet.script.value.StringValue;
import carpet.script.value.Value;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

public class BlockArgument extends Argument
{
    public final BlockValue block;
    @Nullable public final String replacement;

    private BlockArgument(final BlockValue b, final int o)
    {
        super(o);
        block = b;
        replacement = null;
    }

    private BlockArgument(final BlockValue b, final int o, @Nullable final String replacement)
    {
        super(o);
        block = b;
        this.replacement = replacement;
    }

    public static BlockArgument findIn(final CarpetContext c, final List<Value> params, final int offset)
    {
        return findIn(c, params, offset, false, false, false);
    }

    public static BlockArgument findIn(final CarpetContext c, final List<Value> params, final int offset, final boolean acceptString)
    {
        return findIn(c, params, offset, acceptString, false, false);
    }

    public static BlockArgument findIn(final CarpetContext c, final List<Value> params, final int offset, final boolean acceptString, final boolean optional, final boolean anyString)
    {
        return findIn(c, params.listIterator(offset), offset, acceptString, optional, anyString);
    }

    public static BlockArgument findIn(final CarpetContext c, final Iterator<Value> params, final int offset, final boolean acceptString, final boolean optional, final boolean anyString)
    {
        try
        {
            final Value v1 = params.next();
            //add conditional from string name
            if (optional && v1.isNull())
            {
                return new MissingBlockArgument(1 + offset, null);
            }
            if (anyString && v1 instanceof StringValue)
            {
                return new MissingBlockArgument(1 + offset, v1.getString());
            }
            if (acceptString && v1 instanceof StringValue)
            {
                return new BlockArgument(BlockValue.fromString(v1.getString(), c.level()), 1 + offset);
            }
            if (v1 instanceof BlockValue)
            {
                return new BlockArgument(((BlockValue) v1), 1 + offset);
            }
            final BlockPos pos = c.origin();
            if (v1 instanceof ListValue)
            {
                final List<Value> args = ((ListValue) v1).getItems();
                final int xpos = (int) NumericValue.asNumber(args.get(0)).getLong();
                final int ypos = (int) NumericValue.asNumber(args.get(1)).getLong();
                final int zpos = (int) NumericValue.asNumber(args.get(2)).getLong();

                return new BlockArgument(
                        new BlockValue(
                                c.level(),
                                new BlockPos(pos.getX() + xpos, pos.getY() + ypos, pos.getZ() + zpos)
                        ),
                        1 + offset);
            }
            final int xpos = (int) NumericValue.asNumber(v1).getLong();
            final int ypos = (int) NumericValue.asNumber(params.next()).getLong();
            final int zpos = (int) NumericValue.asNumber(params.next()).getLong();
            return new BlockArgument(
                    new BlockValue(
                            c.level(),
                            new BlockPos(pos.getX() + xpos, pos.getY() + ypos, pos.getZ() + zpos)
                    ),
                    3 + offset
            );
        }
        catch (IndexOutOfBoundsException | NoSuchElementException e)
        {
            throw handleError(optional, acceptString);
        }
    }

    public static class MissingBlockArgument extends BlockArgument
    {
        public MissingBlockArgument(final int o, @Nullable final String replacement)
        {
            super(BlockValue.NONE, o, replacement);
        }
    }

    private static InternalExpressionException handleError(final boolean optional, final boolean acceptString)
    {
        String message = "Block-type argument should be defined either by three coordinates (a triple or by three arguments), or a block value";
        if (acceptString)
        {
            message += ", or a string with block description";
        }
        if (optional)
        {
            message += ", or null";
        }
        return new InternalExpressionException(message);
    }

}
