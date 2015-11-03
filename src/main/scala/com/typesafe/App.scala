package com.typesafe

import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorSystem, Props}
import com.ning.http.client.providers.netty.{NettyAsyncHttpProvider, NettyAsyncHttpProviderConfig}
import com.ning.http.client.{AsyncHttpClient, AsyncHttpClientConfig, AsyncHttpProvider}
import org.jboss.netty.channel.ChannelPipeline
import org.jboss.netty.handler.logging.LoggingHandler
import org.jboss.netty.logging.{InternalLogLevel, InternalLoggerFactory, Slf4JLoggerFactory}
import org.slf4j.Logger

import scala.concurrent.duration._

object App {

  private val logger: Logger = org.slf4j.LoggerFactory.getLogger(classOf[App])

  @throws(classOf[InterruptedException])
  def main(args: Array[String]) {
    InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory())

    val name: String = ManagementFactory.getRuntimeMXBean.getName
    logger.info("id = {}", name)

    val actorSystem = ActorSystem()

    // Give some time to connect Yourkit and see the open threads...
    actorSystem.scheduler.scheduleOnce(10 seconds) {
      val numberOfRequests = 1000
      val buildWithHanging: Boolean = true
      //val app = actorSystem.actorOf(WSClientActor.props(numberOfRequests, buildWithHanging))
      val app = actorSystem.actorOf(AsyncHttpClientActor.props(numberOfRequests, buildWithHanging))
      app ! Execute
    }(actorSystem.dispatchers.defaultGlobalDispatcher)
  }
}

case object Execute

trait LeakingSocketActor extends Actor {
  val url: String = "https://playframework.com/documentation/2.4.x/Migration24"

  def execute(): Unit

  def receive = {
    case Execute =>
      execute()
  }

  def numberOfRequests: Int

  def buildWithHanging: Boolean

  def addToBuilder(builder: AsyncHttpClientConfig.Builder): AsyncHttpClientConfig.Builder = {
    val providerConfig = new NettyAsyncHttpProviderConfig()
    val logging = new NettyAsyncHttpProviderConfig.AdditionalPipelineInitializer {
      override def initPipeline(pipeline: ChannelPipeline): Unit = {
        val hexDump = false
        pipeline.addFirst("log", new LoggingHandler(InternalLogLevel.DEBUG, hexDump))
      }
    }
    providerConfig.setHttpsAdditionalPipelineInitializer(logging)
    providerConfig.setHttpAdditionalPipelineInitializer(logging)
    val b = builder.setAsyncHttpClientProviderConfig(providerConfig)

    if (buildWithHanging) {
      b.setAllowPoolingConnections(true)
        .setAllowPoolingSslConnections(true)
        .setMaxRequestRetry(0)
        .setConnectionTTL(4000)
        .setReadTimeout(30000)
        .setRequestTimeout(30000)
        .setFollowRedirect(false)
    } else {
      b
    }
  }
}

object AsyncHttpClientActor {
  def props(numberOfRequests: Int, buildWithHanging:Boolean): Props = Props(new AsyncHttpClientActor(numberOfRequests, buildWithHanging))
}

class AsyncHttpClientActor(val numberOfRequests: Int, val buildWithHanging: Boolean) extends LeakingSocketActor {

  private val logger = org.slf4j.LoggerFactory.getLogger(this.getClass.getName)

  private var client: AsyncHttpClient = _

  private val requestsOpen = new AtomicInteger(0)

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    val builder = new AsyncHttpClientConfig.Builder()

    val config: AsyncHttpClientConfig = addToBuilder(builder).build

    val provider: AsyncHttpProvider = new NettyAsyncHttpProvider(config)
    client = new AsyncHttpClient(provider, config)
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    client.close()
  }

  def execute() {
    for(i <- 1 to numberOfRequests) {
      requestsOpen.incrementAndGet()
      val future = client.prepareGet(url).execute
      future.addListener(new Runnable {
        override def run(): Unit = {
          val count = requestsOpen.decrementAndGet()
          val response = future.get()
          logger.info(s"Listening for response $response, requestsOpen = $count")
        }
      }, context.dispatcher)
    }
  }
}

/*
object WSClientActor {
  def props(numberOfRequests: Int, buildWithHanging:Boolean): Props = Props(new WSClientActor(numberOfRequests, buildWithHanging))
}

class WSClientActor(val numberOfRequests: Int, val buildWithHanging: Boolean) extends LeakingSocketActor {

  private var client: WSClient = _

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    val template = new NingAsyncHttpClientConfigBuilder().build()
    val builder = new AsyncHttpClientConfig.Builder(template)
    val config: AsyncHttpClientConfig = addToBuilder(builder).build()
    client = new NingWSClient(config)
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    client.close()
  }

  def execute(): Unit = {
    for(i <- 1 to numberOfRequests) {
      client.url(url).get()
    }
  }
}
*/
