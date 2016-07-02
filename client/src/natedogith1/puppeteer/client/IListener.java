package natedogith1.puppeteer.client;

public interface IListener {
	public void setNonce(int nonce);
	public void registerReply(int id, int nonce);
	public void connectReply(int id, int nonce);
	public void lookupReply(ServerId[] servers, int nonce);
	
	public abstract class ListenerAdapter implements IListener {
		public void setNonce(int nonce) {}
		public void registerReply(int id, int nonce) {registerReply(id);}
		public void connectReply(int id, int nonce) {connectReply(id);}
		public void lookupReply(ServerId[] servers, int nonce) {lookupReply(servers);}
		public void registerReply(int id) {}
		public void connectReply(int id) {}
		public void lookupReply(ServerId[] servers) {}
	}
}
