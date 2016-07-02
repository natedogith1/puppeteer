# puppeteer

C->S
register(name):nonce
unregister(name,nonce):void
connect(name,nonce):id
connect(name):id -- invalid value if > 1 nonce
lookup(search):{name,nonce}
send(id,data):void
close(id):void
accept():id -- invalid if not avaliable

S->C
connection_avaliable():void
connection_start(name,nonce,id):void
connection_end(id):void
data_received(id,data):void