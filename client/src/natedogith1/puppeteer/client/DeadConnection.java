package natedogith1.puppeteer.client;

public class DeadConnection implements IConnection {
	
	Puppet master;
	
	public DeadConnection(Puppet master) {
		this.master = master;
	}
	@Override
	public void setId(int id) {
		master.close(id);
	}
	@Override
	public void dataRecieved(byte[] data, int id) {
		master.close(id);
	}
	@Override
	public void close(int id) {
		master.close(id);
	}
}
