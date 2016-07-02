package natedogith1.puppeteer.client;

import java.io.IOException;
import java.net.Socket;

public class Server implements IServer{
	public Puppet master;
	public String name;
	public String host;
	public int port;
	public int id;
	
	public Server(Puppet master, String name, String host, int port) {
		this.master = master;
		this.name = name;
		this.host = host;
		this.port = port;
	}
	
	@Override
	public void idAquired(int id, String name) {
		this.id = id;
	}
	@Override
	public IConnection newConnection(int channel, String name, int id) {
		try {
			return new SocketConnection(master, new Socket(host, port));
		} catch (IOException e) {
			return new DeadConnection(master);
		}
	}
}
