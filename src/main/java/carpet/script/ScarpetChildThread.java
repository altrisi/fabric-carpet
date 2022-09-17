package carpet.script;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.locks.LockSupport;

import org.apache.commons.lang3.Validate;

/**
 * Similar to {@link ScarpetThread}, but this comes from a call starting in a non-scarpet thread,
 * that therefore branches execution here to later return
 */
public final class ScarpetChildThread extends ScarpetThread {
	private final SynchronousQueue<Task> queue = new SynchronousQueue<>();
	private final Thread parent;
	private final boolean singleUse;
	private volatile Task runningTask;

	public ScarpetChildThread(Thread parent) {
		super();
		Validate.isTrue(!(parent instanceof ScarpetThread));
		this.parent = parent;
		this.singleUse = false;
	}
	
	// single use constructor
	private ScarpetChildThread() {
		this.parent = Thread.currentThread();
		this.singleUse = true;
	}
	
	/**
	 * Runs the given runnable in a breakpointable thread, returning either when the task has finished or
	 * a breakpoint has parked the thread. The created thread is single-use, for a reusable thread
	 * submit tasks in {@link #submitTask(Runnable)}
	 */
	public static void runBreakpointable(Runnable runnable) {
		ScarpetChildThread t = new ScarpetChildThread();
		t.start();
		t.submitTask(runnable);
	}
	
	@Override
	public void run() {
		try {
			while (true) {
				Task r = queue.take();
				runningTask = r;
				r.task.run();
				if (singleUse) break;
			}
		} catch (InterruptedException e) {
			System.out.println("Shutting down thread " + this + " due to interruption");
		}
	}
	
	@Override
	protected void notifyParked() {
		this.runningTask.alreadyUnparked = true;
	}
	
	/**
	 * Same as {@link #runBreakpointable(Runnable)}, but reusing an existing thread
	 * @param runnable
	 */
	public void submitTask(Runnable runnable) {
		Validate.isTrue(isAlive());
		new Task(runnable).submit();
	}
	
	private final class Task {
		private final Runnable task;
		private volatile boolean started; // = false by default
		private volatile boolean finished; // = false by default
		private volatile Throwable throwable;
		private volatile boolean alreadyUnparked; // = false by default and normally
		
		public Task(Runnable task) {
			Thread submitter = Thread.currentThread();
			Runnable finalTask = () -> {
				try {
					task.run();
				} catch (Throwable t) {
					throwable = t;
				} finally {
					finished = true;
					if (!alreadyUnparked) {
						Validate.isTrue(LockSupport.getBlocker(submitter) == this);
						LockSupport.unpark(submitter);
					}
				}
			};
			this.task = finalTask;
		}
		
		public void submit() {
			ScarpetChildThread thread = ScarpetChildThread.this;
			Validate.isTrue(!started, "task already ran");
			started = true;
			Thread submitter = Thread.currentThread();
			Validate.isTrue(thread.parent == submitter, "invalid thread");
			
			try {
				thread.queue.put(this);
			} catch (InterruptedException e) {
				throw new IllegalStateException("Interrupted while offering task, that should be instant!", e);
			}
			
			// see task code in constructor
			
			if (!finished) {
				LockSupport.park(submitter); // TODO check multithreaded code here
			}
			if (throwable != null) {
				if (throwable instanceof RuntimeException re) {
					throw re;
				} else if (throwable instanceof Error err) {
					throw err;
				} else {
					throw new IllegalStateException(throwable);
				}
			}
		}
	}
}
