package carpet.script;

import carpet.script.Context.Type;
import carpet.script.value.Value;

/** LazyNumber interface created for lazily evaluated functions */
@FunctionalInterface
public interface LazyValue
{
    LazyValue FALSE = (c, t) -> Value.FALSE;
    LazyValue TRUE = (c, t) -> Value.TRUE;
    LazyValue NULL = (c, t) -> Value.NULL;
    LazyValue ZERO = (c, t) -> Value.ZERO;

    public static LazyValue ofConstant(Value val) {
        return new Constant(val);
    }

    Value evalValue(Context c, Context.Type type);

    default Value evalValue(Context c){
        return evalValue(c, Context.Type.NONE);
    }

    @FunctionalInterface
    interface ContextFreeLazyValue extends LazyValue
    {

        Value evalType(Context.Type type);

        @Override
        default Value evalValue(Context c, Context.Type type) {
            return evalType(type);
        }
    }
    
    public interface Named extends LazyValue {
    	String name();
    }
    
    public record Outer(String name) implements Named {
    	@Override
    	public Value evalValue(Context c, Type type) {
    		throw new IllegalStateException("Tried to evaluate 'outer' info as a runtime value");
    	}
    }
    
    public record VarArgs(String name) implements Named {
    	@Override
    	public Value evalValue(Context c, Type type) {
    		throw new IllegalStateException("Tried to evaluate a varargs info as a runtime value");
    	}
    }
    
    public record Variable(String name, Expression expr) implements Named {
		@Override
		public Value evalValue(Context c, Type type) {
			return expr.getOrSetAnyVariable(c, name).evalValue(c, type);
		}
    }


    class Constant implements ContextFreeLazyValue
    {
        Value result;

        public Constant(Value value)
        {
            result = value;
        }

        public Value get() {return result;}

        @Override
        public Value evalType(Context.Type type) {

            return result.fromConstant();
        }
    }
}
