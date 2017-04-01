package com.github.gigurra

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem, PoisonPill, Props, SupervisorStrategy}
import akka.pattern.{Backoff, BackoffSupervisor}
import akka.routing.RoundRobinPool
import com.github.gigurra.WorkerActor.DoWork

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global

class MasterActor extends Actor {
  case object Tick

  private val tickInterval = FiniteDuration(1, TimeUnit.SECONDS)

  println(s"Starting $this:${self.path} with tick interval")

  private val supervisedWorkerProps = BackoffSupervisor.props(Backoff.onFailure(
    childProps = Props[WorkerActor],
    childName = "Worker",
    minBackoff = FiniteDuration(1, TimeUnit.SECONDS),
    maxBackoff = FiniteDuration(60, TimeUnit.SECONDS),
    randomFactor = 0.2
  ))

  private val loop = context.system.scheduler.schedule(FiniteDuration(0, TimeUnit.SECONDS), interval = tickInterval)(self ! Tick)

  // Restart on failure with backoff
  private val workers = context.actorOf(RoundRobinPool(5).props(supervisedWorkerProps))

  // Restart on failure without backoff - Need to explicitly tell the pool to only restart the failing actor - not all of them!
  //private val workers = context.actorOf(RoundRobinPool(5, supervisorStrategy = MasterActor.this.supervisorStrategy).props(Props[WorkerActor]))

  override def supervisorStrategy: SupervisorStrategy = super.supervisorStrategy

  override def receive: Receive = {
    case Tick =>
      println("Master actor Tick!")
      if (math.random < 0.8) {
        workers ! DoWork(success = true)
      } else {
        workers ! DoWork(success = false)
      }
  }

  override def postStop(): Unit ={
    println(s"$this died")
    loop.cancel()
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
    val actorSystem = ActorSystem("my-system")
    val masterActor = actorSystem.actorOf(Props[MasterActor], name = "the-instance")
    val masterActor2 = actorSystem.actorSelection("akka://my-system/user/the-instance")

    actorSystem.scheduler.scheduleOnce(FiniteDuration(5, TimeUnit.SECONDS))(masterActor2 ! PoisonPill)


  }
}
