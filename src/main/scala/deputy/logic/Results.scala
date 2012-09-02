package deputy.logic

import deputy.models.ResolvedDep
import deputy.Deputy
import org.apache.ivy.core.settings.IvySettings
import deputy.models.Result
import deputy.Constants._
import akka.actor.Props
import akka.pattern._
import akka.util.Duration
import akka.dispatch.Await
import dispatch.Http
import akka.dispatch.Future
import java.io.File
import akka.util.Duration
import akka.dispatch.OnFailure

object Results {

  def download(results: List[Result]) = {
    implicit val actorSystem = Deputy.actorSystem
    //val downloadManager = Deputy.actorSystem.actorOf(Props(new DownloadManager(10)))
    val maxWait = "60 minutes" //TODO: conf
    val initiated = results.map { res =>
      res.urls.map { urlString =>
        import dispatch._
        val svc = url(urlString)

        val completedFilename = urlString.split("/").toList.last
        val destination = new File(completedFilename + "." + urlString.hashCode)
        val akkaP = akka.dispatch.Promise[Option[(String, File)]]()
        Http(svc OK as.File(destination)).onComplete { r =>
          r.fold(ex => None, _ => {
            akkaP.complete(Right(Some(completedFilename -> destination))) //I wish I could pass an Either here, but it does not work
            None
          })
        }
        akkaP
      }
    }

    val completedFiles = initiated.map { files =>
      Future.firstCompletedOf(files).map { result =>
        result.foreach {
          case (completedFilename, file) =>
            val dest = new File(completedFilename)
            file.renameTo(dest)
            Deputy.out.println(dest.getAbsolutePath)
        }
      }
    }

    /*
    val urlString = "http://repo.ftypesafe.com/typesafe/releases/io/netty/netty/3.5.0.Final/netty-3.5.0.Final.jar"
    import dispatch.{ Duration => _, _ }
    val svc = url(urlString)

    val completedFilename = urlString.split("/").toList.last
    val destination = new File(completedFilename + "." + urlString.hashCode)
    val akkaP = akka.dispatch.Promise[Option[(String, File)]]()
    val a = Http(svc OK as.File(destination))
    a.onComplete(r => {
      val completeRes = r.right.map(_ => completedFilename -> destination)
      val akkaComplete = completeRes.fold(ex => Right(None), res => Right(Some(res))) //I wish I could pass an Either here, but it does not work
      akkaP.complete(akkaComplete)
    })*/

    //println(a().toString)
    //val future = ask(downloadManager, Start(results))(Duration.parse(maxWait))
    Await.result(Future.sequence(completedFiles), Duration.parse(maxWait))
    //Thread.sleep(10000)
  }

  def fromResolved(all: List[ResolvedDep]) = {
    all.groupBy(_.dep).foreach {
      case (dep, rds) =>
        val moduleTypeMap = rds.groupBy(_.moduleType)
        moduleTypeMap.foreach {
          case (moduleType, rds) =>
            val urls = rds.map(_.path)
            Deputy.out.println(Result(dep, moduleType, urls).format)
        }
    }
  }
}