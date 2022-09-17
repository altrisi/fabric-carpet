package carpet.script;

import static java.lang.Thread.currentThread;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;

public final class LineLocker {
	private final BlockableImpl[] locks;
	
	public LineLocker(int lines) {
		locks = new BlockableImpl[lines];
	}
	
	public Blockable registerLine(int line) {
		if (locks[line] == null) {
			return locks[line] = new BlockableImpl(line);
		}
		return null;
	}
	
	/**
	 * 
	 * @param line The line to add a breakpoint to
	 * @return {@code null} if no breakpoint was added because there was no {@link Blockable} for that line, a {@link Breakpoint} otherwise
	 */
	public Breakpoint addBreakpoint(int line) { // TODO probably take sources and stuff
		if (locks.length > line && locks[line] != null) {
			locks[line].enable();
			return locks[line];
		}
		return null;
	}
	
	public interface Blockable {
		/**
		 * Enters the section this {@link Blockable} is protecting, or blocks the thread if a {@link Breakpoint} is active in it,
		 * adding a {@link BreakpointActivation} to its queue
		 */
		void enterSection();
	}
	
	public interface Breakpoint {
		/**
		 * Polls for a possible activation of this breakpoint and returns it if there's been one since the last call
		 * (or the creation of this breakpoint), or {@code null} if there hasn't been any.
		 * 
		 * Calling this method removes the activation from the queue
		 */
		BreakpointActivation poll();
		
		/**
		 * Disables this breakpoint, without releasing activations that have been polled TODO clearing may not be a good idea, as my own test case demonstrated
		 */
		void disable();
		
		/**
		 * Disables this breakpoint and releases any blocked threads
		 */
		void disableAndRelease();
		
		int line();
	}
	
	public interface BreakpointActivation {
		/**
		 * Releases the thread blocked on this breakpoint
		 */
		void step();
		
		Breakpoint breakpoint();
		
		Thread ownerThread();
	}
	
	public static void main(String[] args) throws InterruptedException {
		final int LINE = 8;
		LineLocker locker = new LineLocker(20);
		Blockable blockable = locker.registerLine(LINE);
		System.out.println("> Starting a thred");
		new Thread(() -> thread(blockable)).start();
		Thread.sleep(100);
		System.out.println("> Toggling breakpoint and starting thred");
		Breakpoint breakpoint = locker.addBreakpoint(LINE);
		new Thread(() -> thread(blockable)).start();
		Thread.sleep(1000);
		System.out.println("> Looking for breakpoint activations");
		BreakpointActivation activation = breakpoint.poll();
		System.out.println(activation);
		Thread.sleep(1000);
		System.out.println("> Starting thread (breakpoint should still be on)");
		new Thread(() -> thread(blockable)).start();
		Thread.sleep(1000);
		System.out.println("> Looking for breakpoint activations");
		BreakpointActivation activation2 = breakpoint.poll();
		System.out.println(activation2);
		System.out.println("> Removing breakpoint without unlocking");
		breakpoint.disable();
		Thread.sleep(1000);
		System.out.println("> Stepping breakpoint on first thread only");
		activation.step();
		Thread.sleep(1000);
		System.out.println("> Stepping breakpoint on second activation");
		activation2.step();
	}
	
	private static void thread(Blockable blok) {
		System.out.println("Starting from " + Thread.currentThread());
		blok.enterSection();
		System.out.println("Finishing from " + Thread.currentThread());
	}
	
	private static class BlockableImpl implements Blockable, Breakpoint {
		private volatile boolean enabled; // = false by default
		private final Set<BreakpointActivationImpl> blockedThreads = ConcurrentHashMap.newKeySet();
		private final ConcurrentLinkedQueue<BreakpointActivation> activationQueue = new ConcurrentLinkedQueue<>();
		private final int line; //TODO decide if needed
		
		public BlockableImpl(int line) {
			this.line = line;
		}
		
		@Override
		public int line() {
			return line;
		}
		
		@Override
		public void enterSection() {
			Thread t;
			if (enabled && isThreadAllowedToPark(t = currentThread())) {
				BreakpointActivationImpl b = new BreakpointActivationImpl(t);
				blockedThreads.add(b);
				activationQueue.add(b);
				if (!b.stepped) { // breakpoint could have been stepped in between
					LockSupport.park(b);
				}
			}
		}

		@Override
		public BreakpointActivation poll() {
			return activationQueue.poll();
		}
		
		// Enabled by LineLocker
		private void enable() {
			this.enabled = true;
		}

		@Override
		public void disable() {
			this.enabled = false;
			// Clear activations that have _not_ been acknowledged, given it's likely this object is going to get thrown away and those threads
			// would be dead TODO maybe don't...
			BreakpointActivation b;
			while ((b = poll()) != null) {
				b.step();
			}
		}

		@Override
		public void disableAndRelease() {
			disable();
			for (BreakpointActivation activation : blockedThreads) {
				activation.step();
			}
			blockedThreads.clear();
		}
		
		private static boolean isThreadAllowedToPark(Thread t) {
			if (t instanceof ScarpetThread) {
				return true;
			}
			System.out.println("Breakpoint failed to apply because of execution in unsupported thread, stacktrace follows:");
			new Throwable().printStackTrace();
			return false;
		}
		
		class BreakpointActivationImpl implements BreakpointActivation {
			volatile boolean stepped; // = false by default, package private to allow parker to check breakpoint wasn't stepped before park call
			private final Thread thread;
			public BreakpointActivationImpl(Thread thread) {
				this.thread = thread;
			}
			
			@Override
			public void step() {
				if (!stepped) {
					stepped = true;
					if (blockedThreads.remove(this)) { //true if it was there
						if (LockSupport.getBlocker(thread) != this) {
							throw new IllegalStateException("Tried to unblock a thread not blocked by us");
						}
						LockSupport.unpark(thread);
					} else {
						throw new IllegalStateException("Almost unparked a non-parked thread, or a race condition happened!");
					}
				}
			}
			
			// Probably useless
			
			@Override
			public Thread ownerThread() {
				return thread;
			}
			
			@Override
			public Breakpoint breakpoint() {
				return BlockableImpl.this;
			}
		}
	}
}
