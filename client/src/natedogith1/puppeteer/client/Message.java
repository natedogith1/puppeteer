package natedogith1.puppeteer.client;

public enum Message {
	RESPONSE(Direction.CLIENT),
	REGISTER(Direction.SERVER),
	UNREGISTER(Direction.SERVER),
	CONNECT(Direction.BOTH),
	CONNECT_NAME(Direction.SERVER),
	LOOKUP(Direction.SERVER),
	SEND(Direction.BOTH),
	CLOSE(Direction.BOTH),
	END_SESSION(Direction.BOTH);
	
	private Direction direction;
	
	Message(Direction direction) {
		this.direction = direction;
	}
	
	public Direction getDirection() {
		return direction;
	}
	
	public static enum Direction {
		SERVER,CLIENT,BOTH;
	};
}
