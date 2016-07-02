package natedogith1.puppeteer.client;

import java.io.IOException;
import java.net.ServerSocket;

public class Client {
	private Puppet master;
	private String name;
	private int id;
	private int port;
	private boolean hasId;
	private ServerSocket serverSocket;
	private Thread receiveThread;
	
	public Client(Puppet master, String name, int localPort){
		this(master, name, localPort, 0, false);
	}
	
	public Client(Puppet master, String name, int localPort, int id){
		this(master, name, localPort, id, true);
	}
	
	private Client(Puppet master, String name, int localPort, int id, boolean hasId){
		this.master = master;
		this.name = name;
		this.id = id;
		this.port = localPort;
		this.hasId = hasId;
		master.registerCloseListener(new Runnable() {
			@Override
			public void run() {
				stop();
			}
		});
		receiveThread = new Thread("port " + localPort + " to " + name + (hasId?" : " + id:"")){
			@Override
			public void run() {
				doAccept();
			}
		};
	}
	
	public void start() {
		receiveThread.run();
	}
	public void stop() {
		try {
			serverSocket.close();
		} catch (IOException e) {
			
		}
	};
	public String getName() {return name;}
	public int getId() {return id;}
	public int getPort() {return port;}
	public boolean hasId() {return hasId;}
	
	private void doAccept() {
		try {
			serverSocket = new ServerSocket(port);
			while ( !serverSocket.isClosed() ) {
				SocketConnection connection = new SocketConnection(master, serverSocket.accept());
				if ( hasId )
					master.connect(name, connection, id);
				else
					master.connect(name, connection);
			}
		} catch (IOException e) {
			
		}
	}
}
