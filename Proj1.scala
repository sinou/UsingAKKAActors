package akka.tutorial.first.scala
 
import akka.actor.{Actor, PoisonPill}
import Actor._
import akka.routing.{Routing, CyclicIterator}
import Routing._
import java.util.Scanner; 
 
import java.util.concurrent.CountDownLatch
 
object Proj1/* extends App */{
  
  
  def main(args: Array[String]){
  calculate(nrOfWorkers = args(0).toInt, nrOfElements = args(1).toInt, nrOfMessages = args(0).toInt)
  }
  // ====================
  // ===== Messages =====
  // ====================
  sealed trait PiMessage
  case object Calculate extends PiMessage
  case class Work(start: Int, nrOfElements: Int) extends PiMessage
  case class Result(value: Array[Int]) extends PiMessage
 
  // ==================
  // ===== Worker =====
  // ==================
  class Worker extends Actor {
 
    // define the work
    def calculatePiFor(start: Int, nrOfElements: Int): Array[Int] = {
      var acc = new Array[Int](4)
      var t: Int = 0
      for (i <- start until (start + nrOfElements)){
        t += i * i
      }
       acc(0) = t
       acc(1) = start
       acc(2) = nrOfElements
       if((Math.sqrt(acc(0))).toInt * (Math.sqrt(acc(0))).toInt != acc(0).toInt){
         acc(3) = 0
       }
       else{
         acc(3) = 1
       }
       acc
    }
 
    def receive = {
      case Work(start, nrOfElements) =>
        self reply Result(calculatePiFor(start, nrOfElements)) // perform the work
    }
  }
 
  // ==================
  // ===== Master =====
  // ==================
  class Master(
    nrOfWorkers: Int, nrOfMessages: Int, nrOfElements: Int, latch: CountDownLatch)
    extends Actor {
 
    var pi: String = ""
    var nrOfResults: Int = _
    var start: Long = _
    var tempCount: Long = _
 
    // create the workers
    val workers = Vector.fill(nrOfWorkers)(actorOf[Worker].start())
 
    // wrap them with a load-balancing router
    val router = Routing.loadBalancerActor(CyclicIterator(workers)).start()
 
    // message handler
    def receive = {
      case Calculate =>
        // schedule work
        //for (start <- 0 until nrOfMessages) router ! Work(start, nrOfElements)
        for (i <- 0 until nrOfMessages) router ! Work((i + 1), nrOfElements)
 
        // send a PoisonPill to all workers telling them to shut down themselves
        router ! Broadcast(PoisonPill)
 
        // send a PoisonPill to the router, telling him to shut himself down
        router ! PoisonPill
 
      case Result(value) =>
        // handle result from the worker
        if(value(3) == 1){
          pi = pi + value(1) + "\n"
          tempCount = value(2)
        }
        nrOfResults += 1
        if (nrOfResults == nrOfMessages) self.stop()
    }
 
    override def preStart() {
      start = System.currentTimeMillis
    }
 
    override def postStop() {
      // tell the world that the calculation is complete
      if(tempCount != 0 ){
      println(
        "\n\tThe sequence of length %s that have sum of squares as perfect squares start from: \n%s \nCalculation time: \t%s millis"
        .format(tempCount, pi, (System.currentTimeMillis - start)))
      }
      else{
        println("none found \n Calculation time: \t\t%s".format((System.currentTimeMillis - start)))
      }
      latch.countDown()
    }
  }
 
  // ==================
  // ===== Run it =====
  // ==================
  def calculate(nrOfWorkers: Int, nrOfElements: Int, nrOfMessages: Int) {
 
    // this latch is only plumbing to know when the calculation is completed
    val latch = new CountDownLatch(1)
 
    // create the master
    val master = actorOf(
      new Master(nrOfWorkers, nrOfMessages, nrOfElements, latch)).start()
 
    // start the calculation
    master ! Calculate
 
    // wait for master to shut down
    latch.await()
  }
}