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
import java.io.FileOutputStream

object Results {

  def download(results: List[Result]) = {
    implicit val actorSystem = Deputy.actorSystem
    //val downloadManager = Deputy.actorSystem.actorOf(Props(new DownloadManager(10)))
    val maxWait = "60 minutes" //TODO: conf
    val initiated = results.map { res =>
      res.urls.filter(url => url.startsWith(DependencyExtractor.HttpProtocol)).map { urlString =>
        import dispatch._
        val svc = url(urlString)

        val completedFilename = urlString.split("/").toList.last
        val destination = new File(completedFilename + "." + urlString.hashCode)
        destination.deleteOnExit()

        val akkaP = akka.dispatch.Promise[Option[(String, File)]]()
        Deputy.debug("Downloading: " + urlString + " to " + destination.getAbsolutePath)
        Http(svc OK as.Bytes).onComplete { r =>
          r.fold(ex => {
            Deputy.debug("Failed while downloading: " + urlString + ". Reason: " + ex.getMessage)
            akkaP.complete(Right(None)) //I wish I could pass an Either directly here, but it does not work (throws the exception and never completes the akka promise)
          }, bytes => {
            val fos = new FileOutputStream(destination)
            try {
              fos.write(bytes)
              akkaP.complete(Right(Some(completedFilename -> destination)))
              None
            } finally {
              fos.close()
            }
          })
        }

        akkaP
      }
    }

    def firstSomeCompleted(files: List[Future[Option[(String, File)]]]): Future[Option[(String, File)]] = {
      val current = Future.firstCompletedOf(files)
      current.flatMap { res =>
        if (res == None) {
          Deputy.debug("Filtering out: " + current + " from " + files)
          firstSomeCompleted(files.filter(_ != current))
        } else {
          Deputy.debug("Got res: " + res)
          Future { res }
        }
      }

    }

    val completedFiles = initiated.map { files =>
      Future.find(files)(_ != None).map { result =>
        result.foreach {
          _.foreach {
            case (completedFilename, file) =>
              val dest = new File(completedFilename)
              file.renameTo(dest)
              Deputy.debug("Finished: " + file.getAbsolutePath)

              Deputy.out.println(dest.getAbsolutePath)
          }
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