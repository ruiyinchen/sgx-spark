package org.apache.spark.sgx;

public abstract class SgxCommunicator {
	
	/**
	 * Send one object.
	 * @param o the object to send
	 */
	public final void sendOne(Object o) {
		write(o);
	}
	
	/**
	 * Receive one object.
	 * @return the received object
	 */
	public final Object recvOne() {
		Object o = read();
		return o;
	}
	
	/**
	 * Send one object and wait for the result of type T.
	 * @param o the object to send
	 * @return the result object of type T
	 */
	@SuppressWarnings("unchecked")
	public final <T> T sendRecv(Object o) {
		sendOne(o);
		Object ret = recvOne();
		return (T) ret;
	}
	
	public abstract void close();
	
	protected abstract Object read();
	
	protected abstract void write(Object o);
}
