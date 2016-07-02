package natedogith1.puppeteer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


public class Server {
	
	public static final int DEAFULT_PORT = 11717; 
	
	private HostDatabase hostDatabase = new HostDatabase();
	private ServerSocket serverSocket;
	private Thread thread;
	
	public Server(int port) throws IOException {
		serverSocket = new ServerSocket(port);
		thread = new Thread() {
			@Override
			public void run() {
				handleConnections();
			}
		};
	}
	
	public void start() {
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
