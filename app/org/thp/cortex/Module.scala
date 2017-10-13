//import com.google.inject.AbstractModule
//import controllers.{ AssetCtrl, AssetCtrlDev, AssetCtrlProd }
//import net.codingwell.scalaguice.ScalaModule
//import play.api.libs.concurrent.AkkaGuiceSupport
//import play.api.{ Configuration, Environment, Mode }
//import services.JobActor
//
//class Module(environment: Environment, configuration: Configuration) extends AbstractModule with ScalaModule with AkkaGuiceSupport {
//
//  override def configure(): Unit = {
//    bindActor[JobActor]("JobActor")
//
//    if (environment.mode == Mode.Prod)
//      bind[AssetCtrl].to[AssetCtrlProd]
//    else
//      bind[AssetCtrl].to[AssetCtrlDev]
//  }
//}
