package org.ferrit.core.uri

import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingDeque}

/**
 * A non-threadsafe implementation of Frontier based on a queue.
 * For a production like environment this would likely be replaced by something 
 * more robust such as an Akka durable message box or Apache Kafka.
 */
class InMemoryFrontier private (maxQueueSize: Int)  extends Frontier {

  /** 
   * Performance note: when the number of queued items exceeds 10k,
   * the mutable Queue outperforms immutable Queue by an order of magnitude.
   */
  private [InMemoryFrontier] var uriQueue = new LinkedBlockingDeque[FetchJob](maxQueueSize)

  override def size:Int = uriQueue.size

  override def enqueue(f: FetchJob):Int = {
    uriQueue.add(f)
//    uriQueue.enqueue(f)
    uriQueue.size
  }

  override def dequeue:Option[FetchJob] = {
    if (uriQueue.isEmpty) None
    else Some(uriQueue.take())
  }

}


object InMemoryFrontier {

  private [InMemoryFrontier]val queues: ConcurrentHashMap[String, InMemoryFrontier] = new ConcurrentHashMap

  def apply(maxQueueSize: Int, crawlerId: String): InMemoryFrontier = {

    queues.putIfAbsent(crawlerId, new InMemoryFrontier(maxQueueSize))
    queues.get(crawlerId)
  }
}