package natedogith1.puppeteer.server;

public class HostInfo {
	private Client client;
	private String name;
	private int id;
	
	public HostInfo(Client client, String name) {
		this.client = client;
		this.name = name;
	}
	
	public Client getClient() {
		return client;
	}
	public String getName() {
		return name;
	}
	public void setId(int id) {
		this.id = id;
	}
	public int getId() {
		return id;
	}
}
