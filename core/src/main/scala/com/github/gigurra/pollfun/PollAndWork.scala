package com.github.gigurra.pollfun

import java.io.IOException
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy, Terminated}

import scala.collection.mutable
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.ExecutionContext.Implicits.global

case object LetsGo
case class PersistenceReturn(data: Seq[String])
case object WorkComplete

class PersistenceActor extends Actor {
  self ! LetsGo
  override def receive: Receive = {
    case LetsGo =>
      println(s"$this fetching data from db")
      if (math.random < 0.5) {
        println(s"$this: OK!")
        context.parent ! PersistenceReturn(Seq("a", "b", "c"))
      } else {
        println(s"$this: FAIL!")
        throw new IOException(s"oh noes, $this failed")
      }
  }
}

class WorkerActor(data: String) extends Actor {
  self ! LetsGo
  override def receive: Receive = {
    case LetsGo =>
      println(s"$this doing work on: '$data'")
      if (math.random < 0.5) {
        println(s"$this: OK!")
        context.parent ! WorkComplete
      } else {
        println(s"$this: FAIL!")
        throw new IOException(s"oh noes, $this failed")
      }
  }
}

class MasterActor extends Actor {

  context.system.scheduler.schedule(Duration.Zero, FiniteDuration(5, TimeUnit.SECONDS))(self ! LetsGo)

  // The empire does not tolerate failure - kill the bastard if he fails
  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(maxNrOfRetries = 0)(SupervisorStrategy.defaultDecider)

  def receive: Receive = idle

  def idle: Receive = {
    case LetsGo =>
      println(s"$this got start command")
      context become waitForDbData(spawnChild(Props[PersistenceActor]))
  }

  def waitForDbData(persistenceActor: ActorRef): Receive = {
    case Terminated(`persistenceActor`) =>
      println(s"Persistence child $persistenceActor died without returning data")
      context become idle
    case PersistenceReturn(data) =>
      println(s"$this got data: '$data' from worker $sender")
      context become waitForWorkers(spawnWorkers(data))
  }

  def waitForWorkers(workersLeft: mutable.HashSet[ActorRef]): Receive = {
    case Terminated(child) =>
      println(s"Worker child $child died before finishing work")
      handleWorkerStopped(child, workersLeft)
    case WorkComplete =>
      println(s"Worker child $sender finished work on data")
      handleWorkerStopped(sender, workersLeft)
  }

  private def spawnWorkers(data: Seq[String]): mutable.HashSet[ActorRef] = {
    mutable.HashSet[ActorRef](data.map(item => spawnChild(Props(new WorkerActor(item)))):_*)
  }

  private def spawnChild(props: Props): ActorRef = {
    context.watch(context.actorOf(props))
  }

  private def handleWorkerStopped(worker: ActorRef, workersLeft: mutable.HashSet[ActorRef]): Unit = {
    workersLeft -= worker
    if (workersLeft.isEmpty) {
      println(s"All workers have stopped, resetting Master Actor")
      context become idle
      for (_ <- 0 until 10) println()
    }
  }
}

object FunWithAkka {
  def main(args: Array[String]): Unit = {
    val actorSystem = ActorSystem("my-system")
    actorSystem.actorOf(Props[MasterActor], name = "the-instance")
  }
}
