package natedogith1.puppeteer.client;

public interface IServer {
	public void idAquired(int id, String name);
	public IConnection newConnection(int channel, String name, int id);
	public void stop(String name, int id);
	
	public abstract class ServerAdapter implements IServer {
		public void idAquired(int id, String name) {idAquired();}
		public IConnection newConnection(int channel, String name, int id) {return newConnection(channel);}
		public void stop(int id, String name) {stop();}
		public void idAquired() {}
		public abstract IConnection newConnection(int channel);
		public void stop() {}
	}
}
