# puppeteer

packets are sent in the order of (id, nonce, sent data)  
RESPONSE packets contain the nonce of the triggering packet and the associated returned data  
name and query are a length followed by an equal number of bytes that form a UTF-8 string  
cid and sid are a 4 byte number

id|direction|name       | sent data |returned data| description
---|------|--------------|-----------|-------------|----
0 | S->C | RESPONSE     | nonce, original packet id, varies  | void   | packet type that holds returned data
1 | C->S | REGISTER     | name           | sid    | offer a service under the given name, returns a value that combines with name to uniquely identify this server
2 | C->S | UNREGISTER   | name, sid      | void   | stop offering the service with the given name and sid
3 | C->S | CONNECT      | name, sid      | cid    | open a connection to the specified service, returns a connection id
3 | S->C | CONNECT      | name, sid, cid | void   | creates a connection to the given service on this host
4 | C->S | CONNECT_NAME | name           | cid    | creates a connection if the service exists and is unique, otherwise returns a cid of 0
5 | C->S |LOOKUP|query|size {name,hid,name,hid,...}| returns a list of the provided size of name,hid pairs 
6 | B->B | SEND         | cid, data      | void   | transmits the given data accross the given connection
7 | B->B | CLOSE        | cid            | void   | closes the given connection
8 | B->B | END_SESSION  | void           | void   | stops the TCP session

the LOOKUP query value accepts '.' as any single character and '*' as any sequence of characters
