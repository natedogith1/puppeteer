package natedogith1.puppeteer.client;

public interface IConnection {
	public void setId(int id);
	public void dataRecieved(byte[] data, int id);
	public void close(int id);
	
	public abstract class ConnectionAdapter implements IConnection {
		public void setId(int id) {setId();}
		public void dataRecieved(byte[] data, int id) {dataRecieved(data);}
		public void close(int id) {close();}
		public void setId() {}
		public void dataRecieved(byte[] data) {}
		public void close() {}
	}
}
