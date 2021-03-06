package natedogith1.puppeteer.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


public class Server {
	
	public static final int DEFAULT_PORT = 11717; 
	
	private HostDatabase hostDatabase = new HostDatabase();
	private ServerSocket serverSocket;
	private Thread thread;
	private int port;
	
	public Server(int port){
		if ( port < 0 )
			port = DEFAULT_PORT;
		this.port = port;
		thread = new Thread("Server Thread") {
			@Override
			public void run() {
				handleConnections();
			}
		};
	}
	
	public void start() throws IOException {
		serverSocket = new ServerSocket(port);
		thread.start();
	}
	
	public int getPort() {
		return serverSocket.getLocalPort();
	}
	
	public void stop() {
		try {
			serverSocket.close();
		} catch (IOException e) {
			// not sure when this would happen;
		}
	}
	
	public boolean isRunning() {
		return thread.isAlive();
	}
	
	public HostDatabase getHostDatabase() {
		return hostDatabase;
	}
	
	private void handleConnections() {
		while (!serverSocket.isClosed()) {
			Socket sock;
			try {
				sock = serverSocket.accept();
				Client client = new Client(this, sock);
				client.start();
			} catch (IOException e) {
				// occurs when serverSocket is closed
			}
		}
	}
	
}
