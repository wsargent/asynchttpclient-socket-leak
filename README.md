# Leaking Socket Issue

if you have 50 requests, then they'll all be closed immediately.
if you have 1000 requests, they'll stay open for a while.
After roughly two minutes, Ning will close off all idle sockets, but up to 30 will never die and
will always be established.

To see the dangling sockets, run the id of the java process:

```
sudo lsof -i | grep 31602
```

You'll see

```
java      31602       wsargent   89u  IPv6 0xe1b25a8062380645      0t0  TCP 192.168.1.106:58646->ec2-54-173-126-144.compute-1.amazonaws.com:https (ESTABLISHED)
```

The client port number is your key into the application: if you search for "58646" in application.log, then you'll
see that there's a connection associated with it:

```
2015-11-02 20:41:38,496 [DEBUG] from org.jboss.netty.handler.logging.LoggingHandler in New I/O worker #1 - [id: 0x5650b318, /192.168.1.106:58646 => playframework.com/54.173.126.144:443] RECEIVED: BigEndianHeapChannelBuffer(ridx=0, widx=2357, cap=2357)
```

You can see the lifecycle of a handle by using grep:

```
grep "0x5650b318" application.log
```

and what's interesting is that while most ids will have a `CLOSE` / `CLOSED` lifecycle associated with them:

```
2015-11-02 20:41:45,878 [DEBUG] from org.jboss.netty.handler.logging.LoggingHandler in Hashed wheel timer #1 - [id: 0x34804fcc, /192.168.1.106:59122 => playframework.com/54.173.126.144:443] WRITE: BigEndianHeapChannelBuffer(ridx=0, widx=69, cap=69)
2015-11-02 20:41:46,427 [DEBUG] from org.jboss.netty.handler.logging.LoggingHandler in New I/O worker #2 - [id: 0x34804fcc, /192.168.1.106:59122 => playframework.com/54.173.126.144:443] CLOSE
2015-11-02 20:41:46,427 [DEBUG] from org.jboss.netty.handler.logging.LoggingHandler in New I/O worker #2 - [id: 0x34804fcc, /192.168.1.106:59122 :> playframework.com/54.173.126.144:443] DISCONNECTED
2015-11-02 20:41:46,434 [DEBUG] from org.jboss.netty.handler.logging.LoggingHandler in New I/O worker #2 - [id: 0x34804fcc, /192.168.1.106:59122 :> playframework.com/54.173.126.144:443] UNBOUND
2015-11-02 20:41:46,434 [DEBUG] from org.jboss.netty.handler.logging.LoggingHandler in New I/O worker #2 - [id: 0x34804fcc, /192.168.1.106:59122 :> playframework.com/54.173.126.144:443] CLOSED
```

In the case of "0x5650b318", there's no CLOSE event happening here.  In addition, there's a couple of lines that say it's a cached channel:

```
2015-11-02 20:41:33,340 [DEBUG] from com.ning.http.client.providers.netty.request.NettyRequestSender in default-akka.actor.default-dispatcher-4 - Using cached Channel [id: 0x5650b318, /192.168.1.106:58646 => playframework.com/54.173.126.144:443]
2015-11-02 20:41:33,340 [DEBUG] from com.ning.http.client.providers.netty.request.NettyRequestSender in default-akka.actor.default-dispatcher-4 - Using cached Channel [id: 0x5650b318, /192.168.1.106:58646 => playframework.com/54.173.126.144:443]
```

So I think Netty is not closing cached channels even if they are idle, in some circumstances.
