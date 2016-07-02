package natedogith1.puppeteer.client;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;

public class SocketConnection implements IConnection {
	
	private Puppet master;
	private Socket socket;
	private int id;
	private Thread readThread;
	
	public SocketConnection(Puppet master, Socket socket) {
		this.master = master;
		this.socket = socket;
	}
	
	@Override
	public void setId(int id) {
		this.id = id;
		if ( id == 0 ) {
			close();
			return;
		}
		readThread = new Thread(){
			@Override
			public void run() {
				handleRead();
			}
		};
		readThread.start();
	}
	
	@Override
	public void dataRecieved(byte[] data, int id) {
		try {
			socket.getOutputStream().write(data);
		} catch (IOException e) {
			
		}
	}
	
	@Override
	public void close(int id) {
		close();
	}
	
	private void close() {
		master.close(id);
		try {
			socket.close();
		} catch (IOException e) {
			
		}
	}
	
	public void handleRead() {
		try {
			byte[] buf = new byte[1024];
			while(!socket.isClosed()) {
				int read;
				read = socket.getInputStream().read(buf);
				master.sendData(id, Arrays.copyOf(buf, read));
			}
		} catch (IOException e) {
			close();
		}
	}
}
