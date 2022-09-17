package carpet.script;

/**
 * A thread used to run scarpet code, that therefore accepts breakpoints
 * @see ScarpetChildThread
 *
 */
public sealed class ScarpetThread extends Thread permits ScarpetChildThread {
	
	protected ScarpetThread() {
		setDaemon(true);
	}

	public ScarpetThread(Runnable r) {
		super(r);
		setDaemon(true);
	}
	
	protected void notifyParked() {
		// noop on thread just for scarpet impl
	}
}
