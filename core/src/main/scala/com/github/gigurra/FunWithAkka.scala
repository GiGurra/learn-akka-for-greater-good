package com.github.gigurra

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem, Props, SupervisorStrategy}
import akka.routing.RoundRobinPool
import com.github.gigurra.WorkerActor.DoWork

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global

class MasterActor extends Actor {
  case object Tick

  private val tickInterval = FiniteDuration(1, TimeUnit.SECONDS)

  println(s"Starting $this with tick interval")

  context.system.scheduler.schedule(FiniteDuration(0, TimeUnit.SECONDS), interval = tickInterval)(self ! Tick)

  // Need to explicitly tell the pool to only restart the failing actor - not all of them!
  private val workers = context.actorOf(RoundRobinPool(5, supervisorStrategy = MasterActor.this.supervisorStrategy).props(Props[WorkerActor]))

  override def supervisorStrategy: SupervisorStrategy = super.supervisorStrategy

  override def receive: Receive = {
    case Tick =>
      println("Master actor Tick!")
      if (math.random < 0.9) {
        workers ! DoWork(success = true)
      } else {
        workers ! DoWork(success = false)
      }
  }
}

class WorkerActor extends Actor {

  println(s"Starting $this")

  override def receive: Receive = {
    case DoWork(success) =>
      if (success) {
        println(s"$this did successful work")
      } else {
        println(s"Oh no - $this is failing")
        throw new RuntimeException(s"$this failed")
      }
  }
}

object WorkerActor {
  case class DoWork(success: Boolean)
}

object FunWithAkka {
  def main(args: Array[String]): Unit = {
    val actorSystem = ActorSystem()
    val masterActor = actorSystem.actorOf(Props[MasterActor])
  }
}
