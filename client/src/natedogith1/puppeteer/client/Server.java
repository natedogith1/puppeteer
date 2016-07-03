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
	
	public void start() {
		master.register(name, this);
	}
	public void close() {
		master.unregister(name, this, id);
	}
	public String getName() {return name;}
	public String getHost() {return host;}
	public int getPort() {return port;}
	public int getId() {return id;}
	
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
	@Override
	public void close(String name, int id) {
		// NO-OP
	}
}
